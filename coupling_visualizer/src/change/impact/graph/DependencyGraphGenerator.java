package change.impact.graph;

import japa.parser.JavaParser;
import japa.parser.ParseException;
import japa.parser.ast.CompilationUnit;
import japa.parser.ast.body.BodyDeclaration;
import japa.parser.ast.body.MethodDeclaration;
import japa.parser.ast.body.TypeDeclaration;
import japa.parser.ast.stmt.BlockStmt;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import change.impact.graph.ast.parser.ASTparser;
import change.impact.graph.commit.Commit;

public class DependencyGraphGenerator {
	//filepath -> ast
	private Map<String,CompilationUnit> currentASTs;
	private Map<String,Set<String>> currentAdjacencyList;
	private Map<String,Method> currentMethods;
	
	public DependencyGraphGenerator() {
		currentASTs = Maps.newHashMap();
	}
	
	public List<CommitGraph> generate(Collection<Commit> commits) throws MalformedURLException, ParseException, IOException {
		List<CommitGraph> commitGraphs = Lists.newArrayList();
		for(Commit commit : commits) {
			CommitGraph commitGraph = new CommitGraph();
			commitGraph.setCommit_SHA(commit.getSha());
			commitGraph.setGraphs(generateGraphsForChangedMethods(commit));
		}
		return commitGraphs;
	}
	
	//TODO: generate ASTs for new/modified classes first. When tracing removed line, 
	//use OLD ast to find method container then use CURRENT ast for building dependencies
	private Collection<DependencyGraph> generateGraphsForChangedMethods(Commit commit) throws MalformedURLException, ParseException, IOException {
		generateCurrentASTs(commit);
		
		//generate dependency graphs for all modified methods in every class
		for(String clazz : commit.getDiffs().keySet()) {
			//keep track of graphs already generated for a method 
			Map<String,DependencyGraph> generated = Maps.newHashMap();
			//generate dependency graph for method with added lines
			for(int lineNumber : commit.getDiff(clazz).getAddedLines().keySet()) {
				CompilationUnit currentAST = currentASTs.get(clazz);
			}
			//generate dependency graph for methods with removed lines
			for(int lineNumber : commit.getDiff(clazz).getRemovedLines().keySet()) {
				
			}
		}
		return null;
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

	public void generateCurrentASTs(Commit commit) throws MalformedURLException, IOException, ParseException {
		for(String clazz : commit.getDiffs().keySet()) {
			InputStream currentCodeSource = null;
			try {
				currentCodeSource = new URL(commit.getDiff(clazz).getNewCode()).openStream();
			} finally {
				currentCodeSource.close();
			}
			CompilationUnit currentAST = JavaParser.parse(currentCodeSource);
			currentASTs.put(clazz, currentAST);
		}
	}

	/**
	 * returns null if method is not part of project
	 * @param method
	 * @return
	 */
	private CompilationUnit getASTforMethod(Method method) {
		return null;
	}
	
}