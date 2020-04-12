import java.io.*;

/*
Knapsack dynamic prog algorithm 
Approach for this try is to process the backwards so only have one array
pros/cons:
it"s more data effiecient
it"s quicker since memory could be close/contiguous and less of it, (assignment data takes 20mins)
there"s no reconstruction possible this way

NOTE: same as non-optimized file, since someone noticed gcd=367 on the data, this now runs in 2.9 seconds
*/

public class w3_asgn_knapsack_optimze {
    
    private static boolean debug = false;
    
    private static int W;
    private static int[] A;
    private static long start;
    
    private static void submitDataFile(String hint, String fname) {
        submitDataFile(hint, fname, -1); // no gcd
    }
    
    private static void submitDataFile(String hint, String fname, int gcd) {
        
		File file = new File(System.getProperty("user.dir"), fname);
        int l = 0;
        String line = null;
        String[] strs = null;
        
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            
            line = br.readLine(); // first line is summary data
            W = Integer.parseInt(line.split("\\s")[0]);
            if (gcd == -1) {
                System.out.println(String.format("knapsack capacity = %d", W));
            }
            else {
                W /= gcd;
                System.out.println(String.format("knapsack capacity = %d, (gcd=%d) so real capacity=%d", W, gcd, W * gcd));
            }
            
            init();
            
            while ((line = br.readLine()) != null) {
                l++;    // for reporting errors
                
                strs = line.split("\\s");
                int v = Integer.parseInt(strs[0]), w = Integer.parseInt(strs[1]);
                
                processItem(v, gcd == -1 ? w : w / gcd);
                
            }

            printResults(fname, hint);
        }
        catch (Exception e) {
            System.out.println(String.format("ERROR happened from line %d (\"%s\", split to \"%s\" and \"%s\" and parsed to %d and %d), in file %s", 
                            l, line, strs[0], strs[1], Integer.parseInt(strs[0]), Integer.parseInt(strs[1]), fname));
			e.printStackTrace();
			System.exit(0);
        }
    }
    
    private static void submitDataArray(String hint, String source, int[] data) {
        
        W = data[0];        // array is pairs of ints
        System.out.println(String.format("knapsack capacity = %d", W));
        init();
        
        for (int i = 2; i < data.length; i += 2) {
            int v = data[i], w = data[i+1];
            if (debug) System.out.println(String.format("  data array i=%d, v=%d, w=%d", i, v, w));
            processItem(v, w);
        }
        
        printResults(source, hint);
    }

    private static void processItem(int v, int w) {
    
        if (w > W) {      // if it blows the capacity on its own, nothing to do
            System.out.println("reject w > W");
            return;
        }

        for (int x = W; x >= 0; x--) {      // NOTE: compute loop, see note above, why this line is W and not W-1

            try {
                if (x >= w) {
                    A[x] = Math.max( A[x], A[x-w] + v );
                }
                else {
                    break;
                }
            }
            catch (ArrayIndexOutOfBoundsException e) {
                System.out.println(String.format("x=%d, w=%d, x-w=%d, v=%d, lenA=%d, W=%d", x, w, x-w, v, A.length, W));
                throw e;
            }
        }
    }

    private static void init() {
        // make an array big enough for all weights + 1
        // weights are >1, not an indexed number from 0, this makes indexing it look like the algo in the lecture (and a bit neater)
        A = new int[W+1];
    }
    
    private static void printResults(String source, String hint) {
        System.out.println(String.format("result=%d, source='%s', hint='%s', elapsed millis=%d", A[A.length-1], source, hint, System.currentTimeMillis() - start));
    }
    
    public static void main(String[] args) {
        start = System.currentTimeMillis();
        if (args.length == 1) {
            if (args[0].equals("q1")) { 
                submitDataFile("answer is 2493893", "w3_knapsack1.txt"); // question 1
            }
            else if (args[0].equals("q2")) { 
                submitDataFile("answer is 4243395", "w3_knapsack_big.txt", 367); // question 2, with gcd
            }
            else {
                System.out.println("usage w3_asgn_knapsack_optimze [q1|q2] or leave param blank for tests");
            }
        }
        else {
            debug = true;
            submitDataArray("expected result: 8", "test1", new int[] { 6,4, 3,4, 2,3, 4,2, 4,3 });
            debug = false;
            submitDataFile("answer is 1398904", "w3_knapsack1-capacity3000.txt"); // question reduced capacity of the knapsack
        }
    }
}
