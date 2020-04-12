import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;

/*
Assignment week 5, traveling salesman problem try using a greedy search
Idea: make a cycle of 3 points, then keep adding a next point one at a time, finding
exactly which edge to cut into to make the shortest new path
*/

public class w5_traveling_salesman_greedy {

    private static final int V1_IDX = 0;
    private static final int V2_IDX = 1;
    private static final int DIST_IDX = 2;
	private static boolean debug = false;
	private static int countDistanceRequests = 0;
    private static long start;	
	private static float totalDistance = 0f, workingDistance = 0f;	// used to move values around to save any object creation 
	private static float[][] inputData;								// used in lots of places, easier as a var
    private static boolean[] visitedSet;
	
    private static void computeShortestTour(String hint, String fname) {
        
		File file = new File(System.getProperty("user.dir"), fname);
        int l = 0;
        
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            
        	String line = br.readLine(); 			// first line is summary data
            String[] summary = line.split("\\s");
            int N = Integer.parseInt(summary[0]);

            float[][] data = new float[N][2];
            
            while ((line = br.readLine()) != null) {
            	String[] strs = line.split("\\s");
                float x = Float.parseFloat(strs[0]), y = Float.parseFloat(strs[1]);
                
                data[l++] = new float[] { x, y };
            }
            
        	computeShortestTour(hint, fname, data);

        }
        catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
        }        
    }	
	
    private static void computeShortestTour(String hint, String source, float[][] data) {
    	
    	inputData = data;
    	visitedSet = new boolean[data.length];
    	countDistanceRequests = 0;							// for report at end
    	distances = new float[data.length][data.length];	// for faster lookups
    	for (float[] dists : distances) {
    		Arrays.fill(dists, Float.NEGATIVE_INFINITY);	// just in case there's a 0 length edge
    	}
    	
    	ArrayList<Object[]> edges = new ArrayList<Object[]>(data.length);
    	
    	// try a greedy approach, make a smallest possible triangle to begin
    	initTriangle(edges, true);
    	if (debug) reportEdges(edges);
    	
    	// add each successive point to the cycle by cutting in where it adds the least distance overall
    	while (countVisited() < data.length) { 	// stop when all accounted for
    		includeNextNearestVertex(edges);
        	if (debug) reportEdges(edges);
    	}
    	
		// phase 2: iterative improvement
    	if (debug) System.out.println("PHASE 2: ############################");
    	itertateImprovement(edges);
    	
		printResults(source, hint, edges);
    	if (debug) reportEdges(edges);
    }

	private static int countVisited() {
		int count = 0;
		for (boolean b : visitedSet) {
			if (b) count++;
		}
		return count;
	}

	private static void itertateImprovement(ArrayList<Object[]> edges) {

		// figure out an improvement over all the edges for each vertex without changing it, if there's one
		// then slice and dice
		
		int movedVertex = -1;
    	do {
    		movedVertex = -1;
    		
    		for (int v = 0; v < inputData.length; v++) {	// start at 0 each time
    			
    			// look for the 2 edges that include v
    			int e1 = -1, e2 = -1;
    			Object[] edge1 = null, edge2 = null;
    			
    			for (int e = 0; e < edges.size(); e++) {
    				
    				Object[] edge = edges.get(e);
    				int v1 = (int)edge[V1_IDX];
    				int v2 = (int)edge[V2_IDX];
    				
    				if (v1 == v || v2 == v) {
    					if (e1 == -1) {
    						e1 = e;
    						edge1 = edge;
    					}
    					else {
    						e2 = e;
    						edge2 = edge;
    					}
    				}
    				
    				if (e1 != -1 && e2 != -1) {
    					break;								// break out when find both edges
    				}
    			}

    			if (edge1 == null || edge2 == null) {
    				throw new RuntimeException(String.format("Must be an error, one of the edges is null, e1=%d, e2=%d", e1, e2));
    			}
    			
    			float testTotalDist = totalDistance, prevTotalDist = totalDistance;	// prev is used for a final double check

    			// simulate chopping out v from the edge
    			testTotalDist -= (float)edge1[DIST_IDX];
    			testTotalDist -= (float)edge2[DIST_IDX];
    			
    			// get v1 and v2 as the other 2 vertices that would be joined
    			int v1 = ((int)edge1[V1_IDX] == v) ? (int)edge1[V2_IDX] : (int)edge1[V1_IDX];
				int v2 = ((int)edge2[V1_IDX] == v) ? (int)edge2[V2_IDX] : (int)edge2[V1_IDX];

    			testTotalDist += getDistance(null, v1, v2);

        		int replEdgeIdx = getBestCandidateEdge(edges, v, testTotalDist, edge1, edge2);
        		
        		if (replEdgeIdx == -1) {						// no better edge found
            		if (debug) System.out.println("No change made");
        			continue;
        		}

    			Object[] replEdge = edges.get(replEdgeIdx); 	// keep hold of the edge, after chopping the other the index will change

    			chopOutV(edges, v, e1, e2, edge1, edge2);

    			spliceEdgeWithV(edges, v, replEdge);

            	if (Float.compare(prevTotalDist, totalDistance) != 0) {
        			System.out.println(String.format("vertex=%d respliced, tour changed from %.4f to %.4f%s", v, prevTotalDist, totalDistance, totalDistance < prevTotalDist ? "" : " - FOR A WORSE VALUE!"));
            		if (debug) {
                		reportEdges(edges);
            		}
        			
        			if (totalDistance < prevTotalDist) {
                		movedVertex = v;						// total distance changed, break out and go around again
                		break;
        			}
            	}
    		}
    	}
    	while (movedVertex != -1);							// keep improving until there's no changes
	}

	private static int getBestCandidateEdge(ArrayList<Object[]> edges, int v, float testTotalDist, Object[] ignoreEdge1, Object[] ignoreEdge2) {

		// find the edge which can be cut into which saves the most distance overall
		int bestEdgeToCut = -1;
		float bestTourDist = Float.MAX_VALUE;
		
		// any better edge must produce a new spliced tour len < the current total
		for (int e = 0; e < edges.size(); e++) {
			
			Object[] edge = edges.get(e);
			
			if (edge.equals(ignoreEdge1) || edge.equals(ignoreEdge2)) {
				continue;
			}
			
			int v1 = (int)edge[V1_IDX];
			int v2 = (int)edge[V2_IDX];
			float edgeDist = (float) edge[DIST_IDX];
			
			float v1To3 = getDistance(null, v1, v);
			float v2To3 = getDistance(null, v2, v);
			
			float newDist = testTotalDist - edgeDist + v1To3 + v2To3;
			
			if (newDist < totalDistance && newDist < bestTourDist) {
				bestTourDist = newDist;
				bestEdgeToCut = e;
			}
		}
		
		return bestEdgeToCut;
	}

	private static void chopOutV(ArrayList<Object[]> edges,
			int v, int e1, int e2, Object[] edge1, Object[] edge2) {
		// got the edges, take the 1st one and change it so that it points to the other end point of the 2nd one
		// cutting v out of the link
		if ((int)edge1[V1_IDX] == v) {
			if ((int)edge2[V1_IDX] == v) {
				edge1[V1_IDX] = edge2[V2_IDX];
			}
			else {
				edge1[V1_IDX] = edge2[V1_IDX];
			}
		}
		else {
			if ((int)edge2[V1_IDX] == v) {
				edge1[V2_IDX] = edge2[V2_IDX];
			}
			else {
				edge1[V2_IDX] = edge2[V1_IDX];
			}
		}
		float newDist = getDistance(null, (int)edge1[V1_IDX], (int)edge1[V2_IDX]);
		
		if (newDist == 0.f) {
			throw new RuntimeException(String.format("Must be an error, new dist=0, e1=%d, e2=%d", e1, e2));
		}
		
		// subtract the distances from the total, and add the new
		totalDistance -= (float)edge1[DIST_IDX];
		totalDistance -= (float)edge2[DIST_IDX];
		totalDistance += newDist;
		
		// change the edges
		edge1[DIST_IDX] = newDist;
		edges.remove(e2);
	}

    private static void reportEdges(ArrayList<Object[]> edges) {
    	// just for debugging
    	
		System.out.println(String.format("report edges total dist=%.2f", totalDistance));
		float checkTotal = totalDistance;
		for (Object[] edge : edges) {
			checkTotal -= (float)edge[DIST_IDX];
			System.out.println(String.format("\tedge v1=%d, v2=%d, dist=%.2f", (int)edge[V1_IDX], (int)edge[V2_IDX], (float)edge[DIST_IDX]));
		}
		if (checkTotal != 0f) {
			System.out.println(String.format("totalized edges doesn't match, diff=%.2f", checkTotal));
		}
	}

	private static void includeNextNearestVertex(ArrayList<Object[]> edges) {
		includeNextNearestVertex(edges, -1);
	}

	private static void includeNextNearestVertex(ArrayList<Object[]> edges, int nearestIdx) {

		if (nearestIdx == -1) {
			nearestIdx = findNextNearestVertex(-1);
		}
		visitedSet[nearestIdx] = true;
		
		// find the edge which can be cut into which saves the most distance overall
		int bestEdgeToCut = -1, bestV1 = -1, bestV2 = -1;
		float bestTourDist = Float.MAX_VALUE, bestV1To3Dist = 0f, bestV2To3Dist = 0f, bestEdgeDist = 0f;
		
		for (int i = 0; i < edges.size(); i++) {
			Object[] edge = edges.get(i);
			int v1 = (int) edge[V1_IDX];
			int v2 = (int) edge[V2_IDX];
			float edgeDist = (float) edge[DIST_IDX];
			
			float v1To3 = getDistance(null, v1, nearestIdx);
			float v2To3 = getDistance(null, v2, nearestIdx);
			
			float newDist = totalDistance - edgeDist + v1To3 + v2To3;
			
			if (newDist < bestTourDist) {
				bestTourDist = newDist;
				bestEdgeToCut = i;
				bestV1 = v1;
				bestV2 = v2;
				bestV1To3Dist = v1To3;
				bestV2To3Dist = v2To3;
				bestEdgeDist = edgeDist;
			}
		}
		
		spliceEdgeWithV(edges, nearestIdx, bestEdgeToCut, bestV1, bestV2, bestV1To3Dist, bestV2To3Dist, bestEdgeDist);
	}

	private static void spliceEdgeWithV(ArrayList<Object[]> edges, int v, Object[] cutEdge) {
				
		for (int e = 0; e < edges.size(); e++) {
			Object[] edge = edges.get(e);
			if (edge.equals(cutEdge)) {
				spliceEdgeWithV(edges, v, e, (int)edge[V1_IDX], (int)edge[V2_IDX], 
						getDistance(null, v, (int)edge[V1_IDX]), getDistance(null, v, (int)edge[V2_IDX]), (float)edge[DIST_IDX]);
			}
		}
		
	}

	private static void spliceEdgeWithV(ArrayList<Object[]> edges, int v, int cutEdge, int v1, int v2, float v1ToVDist, float v2ToVDist, float cutEdgeDist) {

		// decided on an edge to remove
		edges.remove(cutEdge);
		edges.add(new Object[] { v1, v, v1ToVDist } );	// add 2 new edges
		edges.add(new Object[] { v2, v, v2ToVDist } );	// add 2 new edges
		
		totalDistance -= cutEdgeDist;			// update the total distance by removing the edge cut into
		totalDistance += v1ToVDist;			// and adding the 2 new edges
		totalDistance += v2ToVDist;
	}

	private static void initTriangle(ArrayList<Object[]> edges, boolean random) {

		// find the 2 nearest points and create a triangle of the edges with the first point
		int startIdx = random ? 0 : 0;
		
		visitedSet[startIdx] = true; 									// start point is the first one, or random
		int nearestIdx1 = findNextNearestVertex(0);
		visitedSet[nearestIdx1] = true;
		
		edges.add(new Object[] { startIdx, nearestIdx1, workingDistance } ); // edge1 to start
		totalDistance = workingDistance;								// start adding distances for complete cycle
		
		int nearestIdx2 = findNextNearestVertex(0);
		visitedSet[nearestIdx2] = true;
		
		edges.add(new Object[] { startIdx, nearestIdx2, workingDistance } ); // edge2 also to start
		totalDistance += workingDistance;

		float dist3 = getDistance(null, nearestIdx1, nearestIdx2);
		edges.add(new Object[] { nearestIdx1, nearestIdx2, dist3 } );	// edge1 to edge2
		totalDistance += dist3;
	}


	private static int findNextNearestVertex(int fromVertex) {
		
		// called with fromVertex -1 means find the vertex that's closest to any that's already in the set
		
		// not specifically adding to one or the other set, just find the next nearest vertex to either set and add it there
		int nearestIdx = -1;
		float nearestDist = Float.MAX_VALUE;
		
		for (int v = 0; v < inputData.length; v++) {
			
			if (fromVertex != -1) {
				v = fromVertex;					// bit of a hack, overwrite v with from, note also aborts loop afterwards
			}
			
			// test all vertices that have already been added to the set as a source to find the nearest
			if (!visitedSet[v]) {				// ignore not already in a set
				continue;
			}
			
			float[] lastLoc = inputData[v];
			
			// find each vertex that has not yet been added
			for (int i = 1; i < inputData.length; i++) {
				if (visitedSet[i]) {			// ignore already in a set
					continue;
				}
				
				float dist = getDistance(lastLoc, 0, i);
				if (dist < nearestDist) {
					nearestIdx = i;
					nearestDist = dist;
				}
			}
			
			if (fromVertex != -1) {
				break;							// end of that hack
			}
		}
				
		assert (nearestDist != Float.MAX_VALUE && nearestIdx != -1);
		
		workingDistance = nearestDist;
		return nearestIdx;
	}

	private static float[][] distances;
	
	private static float getDistance(float[] fromLoc, int i, int j) {
		
		countDistanceRequests++;
		
		if (!Float.isInfinite(distances[i][j])) {					// returned cached if have it
			workingDistance = distances[i][j];
		}
		else {
			if (fromLoc == null) {
				fromLoc = inputData[i];
			}
			float[] locJ = inputData[j];
			float x = locJ[0] - fromLoc[0];
			float y = locJ[1] - fromLoc[1];
			workingDistance = (float) Math.sqrt(x*x + y*y);
			
			distances[i][j] = distances[j][i] = workingDistance;	// add to cache
		}
		
		return workingDistance;
	}

    private static void printResults(String source, String hint, ArrayList<Object[]> edges) {
    	
		float roundedTotal = 0f;
		for (Object[] edge : edges) {
			roundedTotal += Math.round((float)edge[DIST_IDX]);
		}
    	
		Runtime rt = Runtime.getRuntime();
        System.out.println(String.format("result=%.2f (rounded=%.2f), source='%s', hint='%s', elapsed millis=%d, used memory %dMB, calls to get distance=%d"
        		, totalDistance, roundedTotal, source, hint, System.currentTimeMillis() - start
        		, (rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L), countDistanceRequests));
    }
    
    public static void main(String[] args) {
        start = System.currentTimeMillis();
        if (args.length == 1) {
            if (args[0].equals("w5_tsp.txt")) {
            	computeShortestTour("answer: 26442.73", args[0]); 
            }
            else {
            	computeShortestTour("answer: ?", args[0]);
            }
        }
        else {
        	// this runs fast, so use as a test
        	debug = true;
//        	computeShortestTour("answer: 7.41", "w5_test1.txt");
//            start = System.currentTimeMillis();
//        	computeShortestTour("answer: 4", "internal test1", new float[][] { new float[] {0.0f, 0.0f}, new float[] {0.0f, 1.0f}, new float[] {1.0f, 1.0f}, new float[] {1.0f, 0.0f} });
//            start = System.currentTimeMillis();
//        	computeShortestTour("answer: 10.06", "internal test2", new float[][] { 
//        			new float[] {0.0f, 0.0f}, new float[] {0.0f, 1.0f}, new float[] {1.0f, 1.0f}, new float[] {1.0f, 2.0f}, new float[] {2.0f, 1.0f}, new float[] {4.0f, 2.0f} });
//        	start = System.currentTimeMillis();
//        	computeShortestTour("answer: 91.60", "w5_tsp_testreal.txt");
        	start = System.currentTimeMillis();
        	computeShortestTour("answer: 9765.20", "w5_tsp_testreal2.txt");
        }
    }
}
