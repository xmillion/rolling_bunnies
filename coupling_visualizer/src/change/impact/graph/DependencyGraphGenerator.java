package change.impact.graph;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;

import change.impact.graph.ast.parser.ASTWrapper;
import change.impact.graph.ast.parser.ASTExplorer;
import change.impact.graph.commit.Commit;

public class DependencyGraphGenerator {
	//filepath -> ast
	private Map<String,ASTWrapper> currentASTs;
	private Map<String,ASTWrapper> previousASTs;
	//method id -> [method_id]
	private Map<String,Set<String>> currentAdjacencyList;
	//method id -> Method
	//never removes a method once its added
	private Map<String,Method> currentMethods;

	public DependencyGraphGenerator() {
		currentASTs = Maps.newHashMap();
		previousASTs = Maps.newHashMap();
		currentAdjacencyList = Maps.newHashMap();
		currentMethods = Maps.newHashMap();
	}

	public List<CommitGraph> generate(Collection<Commit> commits) throws MalformedURLException, IOException {
		List<CommitGraph> commitGraphs = Lists.newArrayList();
		for(Commit commit : commits) {
			CommitGraph commitGraph = new CommitGraph();
			commitGraph.setCommit_SHA(commit.getSha());

			Set<String> changedMethods = updateState(commit);

			commitGraph.setGraphs(generateGraphsForChangedMethods(changedMethods));
		}
		return commitGraphs;
	}
	
	Set<String> updateState(Commit commit) throws MalformedURLException, IOException {
		updateASTs(commit);
		Set<String> changedMethods = updateCurrentAdjacencyListAndMethods(commit);
		return changedMethods;
	}

	//TODO: generate ASTs for new/modified classes first. When tracing removed line, 
	//use OLD ast to find method container then use CURRENT ast for building dependencies
	private Collection<DependencyGraph> generateGraphsForChangedMethods(Set<String> changedMethods) {
		Collection<DependencyGraph> graphs = Lists.newArrayList();

		//generate dependency graph for each method
		for(String rootID : changedMethods) {
			DependencyGraph graph = new DependencyGraph();
			Queue<String> frontier = Queues.newPriorityQueue();
			Set<String> frontierSet = Sets.newHashSet();
			//cycle check
			Set<String> visited = Sets.newHashSet();

			frontier.add(rootID);
			frontierSet.add(rootID);


			//BFS
			while(!frontier.isEmpty()) {
				String nodeID = frontier.poll();
				frontierSet.remove(nodeID);

				CommitMethod node = new CommitMethod();
				node.setMethod(currentMethods.get(nodeID));

				if(changedMethods.contains(nodeID)) {
					node.setStatus(ChangeStatus.CHANGED);
				} else {
					node.setStatus(ChangeStatus.UNAFFECTED);
				}

				if(visited.isEmpty())
					graph.setRoot(node);

				Set<String> adjacentNodes = currentAdjacencyList.get(nodeID);
				graph.setAdjacentNodes(node, adjacentNodes);

				visited.add(nodeID);

				for(String adjacentNode : adjacentNodes) {
					if(!frontierSet.contains(adjacentNode) && !visited.contains(adjacentNode)) {
						frontier.add(adjacentNode);
						frontierSet.add(adjacentNode);
					}
				}
			}
			graphs.add(graph);
		}
		return graphs;
	}

	//store method ids only
	private Set<String> filterID(Set<Method> methods) {
		Set<String> ids = Sets.newHashSet();
		for(Method method : methods) {
			boolean unique = ids.add(method.getId());
			assert(unique);
		}
		return ids;
	}

	private Map<String,Set<String>> filterID(Map<Method, Set<Method>> methodMap) {
		Map<String,Set<String>> idMap = Maps.newHashMap();

		for(Method m : methodMap.keySet()) {
			idMap.put(m.getId(), filterID(methodMap.get(m)));
		}

		return idMap;
	}

	public void updateASTs(Commit commit) throws MalformedURLException, IOException {
		//add new and modified ASTs
		Iterable<String> addedOrModified = Iterables.concat(commit.getAddedJavaFiles(), commit.getModifiedJavaFiles());
		for(String clazz : addedOrModified) {
			//update previous AST
			ASTWrapper previousAST = currentASTs.get(clazz);
			previousASTs.put(clazz, previousAST);
			//update current AST
			String url = commit.getDiff(clazz).getNewCode();
			ASTWrapper currentAST = ASTExplorer.generateAST(url);
			currentASTs.put(clazz, currentAST);
		}

		for(String clazz : commit.getRemovedJavaFiles()) {
			previousASTs.put(clazz, currentASTs.get(clazz));
			currentASTs.remove(clazz);
		}
	}

	private Set<String> updateCurrentAdjacencyListAndMethods(Commit commit) {
		Set<String> changedMethods = Sets.newHashSet();
		for(String clazz : commit.getDiffs().keySet()) {
			//generate dependency graph for method with added lines
			ASTWrapper currentAST = currentASTs.get(clazz);

			Map<Integer,String> addedLines = commit.getDiff(clazz).getAddedLines();
			Map<Method, Set<Method>> adjacentNodes = ASTExplorer.getMethodsCalledByMethodsInLines(addedLines.keySet(), clazz);

			Map<Integer,String> removedLines = commit.getDiff(clazz).getRemovedLines();
			Map<Method, Set<Method>> adjacentNodesFromRemovedLines = ASTExplorer.getMethodsCalledByMethodsInRemovedLines(removedLines.keySet(), clazz);

			adjacentNodes.putAll(adjacentNodesFromRemovedLines);
			updateCurrentMethods(adjacentNodes);

			Map<String, Set<String>> strAdjacentNodes = filterID(adjacentNodes);
			currentAdjacencyList.putAll(strAdjacentNodes);

			changedMethods.addAll(strAdjacentNodes.keySet());
		}

		return changedMethods;
	}

	//adds all methods from the map into the currentMethods
	private void updateCurrentMethods(Map<Method, Set<Method>> methodMap) {
		for(Method node : methodMap.keySet()) {
			currentMethods.put(node.getId(), node);
			for(Method adjacentNode : methodMap.get(node)) {
				currentMethods.put(adjacentNode.getId(), adjacentNode);
			}
		}
	}

	//removes all adjacent methods which are not part of the project
	private void filterProjectMethods(Map<Method, Set<Method>> methodMap) {
		for(Method root : methodMap.keySet()) {
			Set<Method> adjacentNodes = methodMap.get(root);
			for(Method adjacentNode : adjacentNodes) {
				if(!isProjectMethod(adjacentNode.getId())) {
					adjacentNodes.remove(adjacentNode);
				}
			}
		}
	}

	private boolean isExistingMethod(String id) {
		return currentMethods.containsKey(id);
	}

	private boolean isProjectMethod(String id) {
		return getASTforMethod(id) != null;
	}
	/**
	 * returns null if method is not part of project
	 * @param method
	 * @return
	 */
	private ASTWrapper getASTforMethod(String id) {
		return null;
	}

	private void addAdjacentNode(String node, String adjacent) {
		Set<String> adjacentNodes = currentAdjacencyList.get(node);
		if(node == null) {
			adjacentNodes = Sets.newHashSet();
			currentAdjacencyList.put(node, adjacentNodes);
		}
		adjacentNodes.add(adjacent);
	}
}
