
/**
 * A really cool way to enumerate all the permutations of a number of bits
 *
 */
public class Gospers_hack {

	public static void main(String[] args) {
		
		int x = 1 << 5;
		
		printBinary(x, "arbitrary x, is a power of 2 = "+((x & (x-1))==0));
		
		x |= 1 << 2;
		x |= 1 << 3;
		
		printBinary(x, "arbitrary x, is a power of 2 = "+((x & (x-1))==0));

		printBinary(-x, "-x");

		int y = x & -x;
		
		printBinary(y, "y = x & -x");
		
		int c = x + y;
		
		printBinary(c, "c = x + y");
		
		while (x > 0) {						// when it hits the negative bit, it will reduce a single bit
			y = x & -x;
			c = x + y;
			x = (((x ^ c) >> 2) / y) | c;
			printBinary(x, "");
		}
	}
	
	/**
	 * Left pads to 32 zeros
	 * @param val
	 * @param desc
	 */
	public static void printBinary(int val, String desc) {
		String unpadded = Integer.toBinaryString(val);
		System.out.println(
				"00000000000000000000000000000000".substring(unpadded.length())
				+unpadded+" : "+desc+ " value="+val);
	}

}
