/** Main Evaluator for TREC Interactive and CLUEWEB Diversity tracks
 *   
 * @author Scott Sanner (ssanner@gmail.com)
 */

package trec.evaldiv;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import diversity.ResultListSelector;

import trec.evaldiv.doc.Doc;
import trec.evaldiv.loss.AllUSLoss;
import trec.evaldiv.loss.AllWSLoss;
import trec.evaldiv.loss.AspectLoss;
import util.VectorUtils;

public class Evaluator {

	public static final boolean USE_ALL_DOCS = true;

	public static final String OUTPUT_FILENAME = "files/trec/RESULTS/trec6-8avg.txt";
	public static final boolean DEBUG = true;
	
	public static void doEval(
			List<String> query_names, 
			HashMap<String,Doc> docs, 
			HashMap<String,Query> query_content, 
			HashMap<String,QueryAspects> query_aspects,
			List<AspectLoss> loss_functions,
			List<ResultListSelector> tests,
			int num_results) throws Exception {
		
		PrintStream ps = new PrintStream(new FileOutputStream(OUTPUT_FILENAME));
		
		// Loop:
		// - go through each test t (a variant of MMR)
		//     - go through all queries q
		//        - add docs to test t for q
		//        - get result list for query q on test t
		//            - go through all loss functions l
		//                - evaluate loss
		
		int test_num = 0;
		for (ResultListSelector t : tests) {
			
			if (DEBUG)
				System.out.println("- Processing test '" + t.getDescription() + "'");

			// Maintain average US and WSL vectors
			double[] usl_vs_rank = new double[num_results];
			double[] wsl_vs_rank = new double[num_results];
			
			for (String query : query_names) {

				// Get query relevant info
				Query q = query_content.get(query);
				QueryAspects qa = query_aspects.get(query);
				if (DEBUG) {
					System.out.println("- Processing query '" + query + "'");
					System.out.println("- Query details: " + q);
					//System.out.println("- Query aspects: " + qa);
				}
				
				// Add docs for query to test
				t.clearDocs();
				Set<String> relevant_docs = null;
				if (USE_ALL_DOCS)
					relevant_docs = docs.keySet();
				else 
					relevant_docs = qa.getRelevantDocs();
				for (String doc_name : relevant_docs) {
					Doc d = docs.get(doc_name);
					t.addDoc(doc_name, d.getDocContent());
					//if (DEBUG)
					//	System.out.println("- Adding " + doc_name + " -> " + d.getDocContent());
				}
				
				// Get the results
				ArrayList<String> result_list = t.getResultList(q.getQueryContent(), num_results);
				if (DEBUG)
					System.out.println("- Result list: " + result_list);
				
				// Evaluate all loss functions on the results
				for (AspectLoss loss : loss_functions) {
					Object o = loss.eval(qa, result_list);
					String loss_result_str = null;
					if (o instanceof double[]) {
						loss_result_str = VectorUtils.GetString((double[])o);
					} else {
						loss_result_str = o.toString();
					}
					
					// Display results to screen for now
					System.out.println("==================================================");
					System.out.println("Query: " + q._name + " -> " + q.getQueryContent());
					System.out.println("MMR Alg: " + t.getDescription());
					System.out.println("Loss Function: " + loss.getName());
					System.out.println("Evaluation: " + loss_result_str);
					
					// Maintain averages
					if (loss instanceof AllUSLoss)
						usl_vs_rank = VectorUtils.Sum(usl_vs_rank, (double[])o);
					if (loss instanceof AllWSLoss)
						wsl_vs_rank = VectorUtils.Sum(wsl_vs_rank, (double[])o);
				}
			}
			t.clearDocs();
			
			usl_vs_rank = VectorUtils.ScalarMultiply(usl_vs_rank, 1d/query_names.size());
			wsl_vs_rank = VectorUtils.ScalarMultiply(wsl_vs_rank, 1d/query_names.size());
			
			System.out.println("==================================================");
			++test_num;
			System.out.println("Exporting " + test_num + ": " + t.getDescription());
			ps.print("" + test_num);
			for (int i = 0; i < usl_vs_rank.length; i++)
				ps.print("\t" + usl_vs_rank[i]);
			for (int i = 0; i < wsl_vs_rank.length; i++)
				ps.print("\t" + wsl_vs_rank[i]);
			ps.println();
		}
		
		ps.close();
	}
	
}
