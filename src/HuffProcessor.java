import java.util.Arrays;
import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */


	public int[] readForCounts(BitInputStream in){
		int[] ret = new int[ALPH_SIZE + 1];
		ret[PSEUDO_EOF] = 1;
		while (true){
			int val = in.readBits(BITS_PER_WORD);
			//System.out.println("" + (char)val);
			if (val == -1) break;
			ret[val] += 1;
		}
		return ret;
	}

	public HuffNode makeTreeFromCounts(int[] counts){
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();

		for (int i = 0; i < counts.length; i++) {
			if (counts[i] > 0) {
				//System.out.println("Adding " + (char)i);
				pq.add(new HuffNode(i, counts[i], null, null));
			}
		}

		while (pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(0, left.myWeight + right.myWeight, left, right);
			pq.add(t);
			//System.out.println(pq);
		}

		return pq.remove();
	}

	public void codingHelper(HuffNode root, String path, String[] encodings){
		if (root.myLeft == null && root.myRight == null) {
			encodings[root.myValue] = path;
			//System.out.println("" + root + " " + path);

			return;
		}
		codingHelper(root.myLeft, path + "0", encodings);
		codingHelper(root.myRight, path + "1", encodings);
	}

	public String[] makeCodingsFromTree(HuffNode root){
		String[] encodings = new String[ALPH_SIZE + 1];
		codingHelper(root, "", encodings);
		return encodings;
	}

	public void writeHeader(HuffNode root, BitOutputStream out){
		if (root == null) {
			return;
		}
		if (root.myLeft == null && root.myRight == null) {
			//System.out.println(root);
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD + 1, root.myValue);
		}
		out.writeBits(1, 0);
		writeHeader(root.myLeft, out);
		writeHeader(root.myRight, out);
	}

	public void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out){
		//System.out.println("Codings array: ");
		//System.out.println(Arrays.toString(codings));
		while (true) {
			int chunk = in.readBits(BITS_PER_WORD);
			//System.out.println((char)chunk);
			if (chunk == -1) {
				String code = codings[PSEUDO_EOF];
				//System.out.println("Writing PSEUDO_EOF" + code);

				out.writeBits(code.length(), Integer.parseInt(code, 2));
				break;
			}
			String code = codings[chunk];
			//System.out.println("Writing " + code);
			out.writeBits(code.length(), Integer.parseInt(code, 2));

		}
	}


	public void compress(BitInputStream in, BitOutputStream out){

		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);
		writeHeader(root, out);

		in.reset();
		writeCompressedBits(codings, in, out);
		out.close();

	}
	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */

	public HuffNode readTree(BitInputStream in){
		int bit = in.readBits(1);
		if (bit == -1) {
			throw new HuffException("bad input");
		}
		if (bit == 0) {
			HuffNode left = readTree(in);
			HuffNode right = readTree(in);
			return new HuffNode(0, 0, left, right);
		} else {
			int value = in.readBits(BITS_PER_WORD + 1);
			return new HuffNode(value, 0, null, null);
		}
	}


	public void decompress(BitInputStream in, BitOutputStream out){

		int magic = in.readBits(BITS_PER_INT);
		if (magic != HUFF_TREE) {
			throw new HuffException("invalid magic number " + magic);
		}
		//out.writeBits(BITS_PER_INT,magic);
		HuffNode root = readTree(in);
		HuffNode current = root;
		while (true){
			int val = in.readBits(1);
			//System.out.println(val);
			if (val == -1) {
				throw new HuffException("bad input, no PSEUDO_EOF");
			}

			if (val == 0) {
				current = current.myLeft;
			} else {
				current = current.myRight;
			}

			if (current.myLeft == null && current.myRight == null) {
				if (current.myValue == PSEUDO_EOF) {
					break;
				} else {
					out.writeBits(BITS_PER_WORD, current.myValue);
					current = root;
				}
			}


		}
		out.close();
	}
}