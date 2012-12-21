package org.quadpixels;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TommyHelper {
	public static CPU cpu = null;
	public static String my_line;
	public static int NUM_INSTS_SKIP = 237000;
	public static int NUM_INSTS = 1000;
	public static int MEMORY_SNAPSHOT_INTERVAL = 100;
	public static float SLOWDOWN = 1;
	public static int IS_LOG_DISASSEMBLY = 1;
	public static int IS_LOG_MEM = 1;
	public static int UP_POKE_NUMINST=-1;
	
	public static void syncWithSim800() {
		File f = new File("C:\\Users\\nitroglycerine\\Downloads\\Sim800_src_hotkey\\Sim800\\config.txt");
		FileReader fr;
		try {
			fr = new FileReader(f);
			BufferedReader br = new BufferedReader(fr);
			{
				String s = br.readLine();
				NUM_INSTS_SKIP = Integer.parseInt(s.substring(s.indexOf("=")+1));
			}
			

			{
				String s = br.readLine();
				NUM_INSTS= Integer.parseInt(s.substring(s.indexOf("=")+1));
			}
			
			{
				String s = br.readLine();
				MEMORY_SNAPSHOT_INTERVAL= Integer.parseInt(s.substring(s.indexOf("=")+1));
			}
			
			{
				String s = br.readLine();
				SLOWDOWN = Integer.parseInt(s.substring(s.indexOf("=")+1));
			}
			
			{
				String s = br.readLine();
				IS_LOG_DISASSEMBLY = Integer.parseInt(s.substring(s.indexOf("=")+1));
			}
			
			{
				String s = br.readLine();
				IS_LOG_MEM = Integer.parseInt(s.substring(s.indexOf("=")+1));
			}
			
			{
				String s = br.readLine();
				UP_POKE_NUMINST = Integer.parseInt(s.substring(s.indexOf("=")+1));
			}
			
			System.out.print("Read conf!\n"
					+ "NUM_INSTS=" + NUM_INSTS
					+ "\nNUM_INSTS_SKIP=" + NUM_INSTS_SKIP
					+ "\nMEMORY_SNAPSHOT_INTERVAL="+MEMORY_SNAPSHOT_INTERVAL
					+ "\nSLOWDOWN="+SLOWDOWN
					+ "\nIS_LOG_DISASSEMBLY="+IS_LOG_DISASSEMBLY
					+ "\nIS_LOG_MEM="+IS_LOG_MEM
					+ "\nUP_POKE_NUMINST="+UP_POKE_NUMINST);
			
			br.close();
		} catch (FileNotFoundException e) {
			System.err.println("Conf file does not exist; defaulting to disabling all developer debug options.");
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	
	final static boolean is_debug = false;
	static void DBG(String s) {
		if(is_debug) System.out.println(s);
	}

	public static boolean[] replay_updowns;
	public static byte[] replay_keycodes;
	public static int[] replay_delays;
	public static int next_reply_tick = 0;
	public static int replay_idx = 0;
	public static int num_replay_actions = 0;
	static void openReplay() {
		try {
			// 1. Count number of lines
			File replay_file;
			replay_file = new File("C:\\Users\\nitroglycerine\\Downloads\\Sim800_src_hotkey\\Sim800\\replay.txt");
			FileReader fr = new FileReader(replay_file);
			BufferedReader br = new BufferedReader(fr);
			br.mark(0);
			while(br.ready()) {
				br.readLine();
				num_replay_actions++;
			}
			System.out.println(String.format("%d actions in replay file.", num_replay_actions));
			
			// 2. Populate replay actions
			replay_updowns = new boolean[num_replay_actions];
			replay_keycodes= new byte[num_replay_actions];
			replay_delays = new int[num_replay_actions];
			
			
			fr = new FileReader(replay_file);
			br = new BufferedReader(fr);
			int idx = 0;
			while(br.ready()) {
				String line = br.readLine();
				String[] sp = line.split(" "); // Delay KeyCode IsDown
				replay_delays[idx] = Integer.parseInt(sp[0]);
				replay_keycodes[idx] = Byte.parseByte(sp[1]);
				replay_updowns[idx] = Integer.parseInt(sp[2]) == 0 ? false : true;
				idx++;
			}
			assert(idx == num_replay_actions);
		} catch (IOException e) { 
			e.printStackTrace(); 
			}
	}
	
	
	
	/* For comparing with Banxian's dump file */
	// Instruction dump
	private static File banxian_dump;
	private static FileReader fr;
	private static BufferedReader br;
	public static int curr_line = 1;
	
	// Biscuits dump
	private static File banxian_biscuit;
	private static FileReader fr_bisc;
	private static BufferedReader br_bisc;
	public static int curr_line_bisc = 1;

	public static void reportIfTraceFileConsumed() {
		try {
			br.readLine(); br_bisc.readLine();
			if(br.ready()==false) {
				System.out.println("Instruction trace file 100% checked.");
			} else {
				System.out.println("Instruction trace file NOT 100% checked.");
			}
			System.out.println("Instruction trace file curr_line="+curr_line);
			if(br_bisc.ready()==false) {
				System.out.println("Memory trace file 100% checked.");
			} else {
				System.out.println("Memory trace file NOT 100% checked.");
			}
			System.out.println("Memory trace file curr_line="+curr_line_bisc);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	static void openBanxianDump() {
		try {
			banxian_dump = new File("C:\\Users\\nitroglycerine\\Downloads\\Sim800_src_hotkey\\Sim800\\Sim800.txt");
			fr = new FileReader(banxian_dump);
			br = new BufferedReader(fr, 100);
			
			banxian_biscuit = new File("C:\\Users\\nitroglycerine\\Downloads\\Sim800_src_hotkey\\Sim800\\BiscuitDump.txt");
			fr_bisc = new FileReader(banxian_biscuit);
			br_bisc = new BufferedReader(fr_bisc, 100);
		} catch (Exception e) {
			
		}
	}
	static void dumpAndCompare(String my_line) throws Exception {
		try {
			String ref_line = br.readLine(); // Banxian's dump.
			String[] ref_tuples = ref_line.split("[\\ \\t]+");
			int n_r = ref_tuples.length;
			String[] my_tuples = my_line.split("[\\ \\t]+");
			int n_m = my_tuples.length;
			
			Exception ex_to_throw = null;
			
			// PS
			if(ref_tuples[n_r - 1].equals(my_tuples[n_m - 1])==false) {
				System.err.println(my_line);
				System.err.println(ref_line);
				ex_to_throw = new Exception(String.format("At line %d, PS dont agree (%s should be %s)",
						curr_line, my_tuples[n_m-1], ref_tuples[n_r-1]));
			}
			
			// Stack Ptr
			if(ref_tuples[n_r - 2].equals(my_tuples[n_m - 2])==false) {
				System.err.println(my_line);
				System.err.println(ref_line);
				ex_to_throw = new Exception(String.format("At line %d, SP dont agree (%s should be %s)",
						curr_line, my_tuples[n_m-2], ref_tuples[n_r-2]));
			}
			
			// Y
			if(ref_tuples[n_r - 3].equals(my_tuples[n_m - 3])==false) {
				System.err.println(my_line);
				System.err.println(ref_line);
				ex_to_throw = new Exception(String.format("At line %d, Y dont agree (%s should be %s)",
						curr_line, my_tuples[n_m-3], ref_tuples[n_r-3]));
			}
			
			// X
			if(ref_tuples[n_r - 4].equals(my_tuples[n_m - 4])==false) {
				System.err.println(my_line);
				System.err.println(ref_line);
				ex_to_throw = new Exception(String.format("At line %d, X dont agree (%s should be %s)",
						curr_line, my_tuples[n_m-4], ref_tuples[n_r-4]));
			}
			
			// A
			if(ref_tuples[n_r - 5].equals(my_tuples[n_m - 5])==false) {
				System.err.println(my_line);
				System.err.println(ref_line);
				ex_to_throw = new Exception(String.format("At line %d, A dont agree (%s should be %s)",
						curr_line, my_tuples[n_m-5], ref_tuples[n_r-5]));
			}
			
			// PC
			if(ref_tuples[0].equals(my_tuples[1])==false) {
				System.err.println(my_line);
				System.err.println(ref_line);
				ex_to_throw = new Exception(String.format("At line %d, PC dont agree (%s should be %s)",
						curr_line, my_tuples[1], ref_tuples[0]));
			}
			
			if(ex_to_throw != null) {
				System.err.println("### Zero page memory:###");
				for(int i=0; i<256; i++) {
					System.err.print(String.format("%02X ",
						cpu.theFleurDeLisDriver.getByte(i)));
					if((i%16)==15) {System.err.print("\n");}
				}
				System.err.println("");
				throw(ex_to_throw);
			}
			
			curr_line += 1;
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	static void dumpAndCompareBiscuit(String my_line) throws Exception {
		try {
			if(br_bisc.ready()==false) return;
			String ref_line = br_bisc.readLine();
			for(int i=0; i<my_line.length(); i++) {
				if(ref_line.charAt(i) != my_line.charAt(i)) {
					StringBuilder caret = new StringBuilder();
					for(int j=0; j<i; j++) caret.append(" ");
					caret.append("^");
					StringBuilder dumps = new StringBuilder();
					dumps.append(String.format("At Biscuilt line %d, two dumps don't agree @ Pos %d!", 
						curr_line_bisc, (caret.length() / 3)));
					dumps.append("\n" + String.format("Look into instruction trace line %d (Inst #%d)\n",
						cpu.total_inst_count-TommyHelper.NUM_INSTS_SKIP, cpu.total_inst_count));
					int diffidx = caret.length()/3;
					boolean linediff = false;
					{
						int cnt = -1; int ix=0;
						StringBuilder my = new StringBuilder(), bx = new StringBuilder();
						my.append("My Dump: \n"); bx.append("Ref Dump:\n");
						while(ix < my_line.length()) {
							if(!("ABCDEF0123456789".contains(""+(my_line.charAt(ix))))) {
								if((cnt==-1) || ((cnt % 16) == 15)) {

									if(linediff==true) {
										my.append(String.format(" <--- (%d)",diffidx));
										linediff=false;
									}
									my.append("\n");
									bx.append("\n");
								}
								cnt++;
							}
							if(cnt==diffidx) linediff=true;
							my.append(my_line.charAt(ix));
							bx.append(ref_line.charAt(ix));
							ix+=1;
						}
						dumps.append(my.toString());
						dumps.append(bx.toString());
					}
					dumps.append("\nInstruction: " + TommyHelper.my_line);
					throw new Exception(dumps.toString());
				}
			}
			curr_line_bisc+=1;
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
}
