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
	//
	// Note:
	//   Those debug mechanisms are specific to my laptop. Specifically, I have changed BanXian's emulator so that it prints out
	//   the disassembly log and zero page memory snapshots, and replays keystrokes by reading a "replay" file.
	//   I have changed this Java port so that it prints out and compares the disassembly log and zero page memory snapshots,
	//   replays keystrokes by reading the same replay file.
	//   
	//   Banxian's emulator and this Java port read the same config file.
	//
	//   These debugging features are for learning purposes and are quite crude.
	//
	//   To DISABLE disassembly and memory checking, write this in the config file:
	//   IS_LOG_DISASSEMBLY=0
	//   IS_LOG_MEM=0
	//   
	//   And you can play around with the emulator :D
	//
	//   The "replay" file would be loaded no matter what. It's like 按键精灵, it automagically presses the key with the
	//     indicated MATRIX CODE. The first column is delay in number of instructions; the second is the matrix code,
	//     the third line is 0 or 1 (release or pressed down)
	//   In my experience, this replay is useful for finding out bugs. For example I had a bug after pressing 输入 20 times
	//     in 名片 . It'd be tedious to repeat pressing the key 20 times every time you debug, plus, you have to press the
	//     keys at the same exact time to reveal the bug. That's where the replay mechanism comes into play.
	//
	//   If trace file comparison is enabled, execution is considered OK if the following is outputted to the console:
	//  ##### Trace file checking status: ######
	// Instruction trace file 100% checked.
	// Instruction trace file curr_line=10001
	// Memory trace file 100% checked.
	// Memory trace file curr_line=2007
	//
	//   It says 100% so it completed comparing all the instructions in the log file without a hitch.
	//   The program terminates in a violent way by throwing exceptions. This is not an error, I did this because I was very lazy. (I know
	//     it's a bad habit and it's not elegant)
	//   Trace comparison is VERY SLOW because a lot of java.lang.String's are being created. It's very slow!!
	//
	// Note: different NMI settings would generate DIFFERENT logs. In this example the 1000001st to 1010000th instructions were compared.
	//   If you want to compare against Banxian's emulator, you may wish to change the NMI settings as well.
	//   
	
	// Working directory.
	// !!!!! NOTE: I'm tesing in Windows so file separator is "\\". In Linux it would be "/".
	private static final String WORKING_DIR = ".\\";
	
	// Please change this path correspondingly !
	// This file says whether we load a replay file, a disassembly log, and a series of memory snapshots.
	private static final String CONFIG_FILE_PATH = WORKING_DIR +"config.txt";

	// This file says where the replay file lives.
	private static final String REPLAY_FILE_PATH = WORKING_DIR + "replay.txt";

	// This is disassembly log file, which looks like the following:
	// 0570  A5C7    LDA  $C7       00 01 1F 01F6 00100011
	// 0572  3014    BMI  $588      00 01 1F 01F6 00100011
	// 0574  EC6B04  CPX  $046B     00 01 1F 01F6 00100011
	// 0577  D00F    BNE  $588      00 01 1F 01F6 00100011
	// 0579  ADAC04  LDA  $04AC     00 01 1F 01F6 00100011
	//
	// The columns are, from left to right: memory locations, machine codes, instructions, A, X, Y, SP and State flags.
	private static final String DISASSEMBLY_LOG_FILE_PATH = WORKING_DIR + "Sim800.txt";
	
	// This is memory snapshots of zero page varaibles. It is taken after every X instructions as indicated in the config file.
	// It looks like this (it is quite long!)
	// 1000005 05 A8 00 00 0A D8 9C 20 00 00 10 03 4F 54 00 00 00 00 00 00 00 00 00 00 00 04 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 0C 00 01 77 00 00 D8 06 1F 19 00 1B CD FF 1E 1E 1F 2A 0C 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 14 06 0C 00 00 77 00 00 00 03 03 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 77 0B 20 01 5A 0B 30 00 03 03 00 00 E5 4E C0 19 00 00 B5 55 3C 0E 10 01 02 19 00 00 00 00 01 00 00 E0 04 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 FF 00 00 00 00 00 00 33 41 01 00 00 00 F2 A8 00 00 00 00 92 00 FF FF FF FF FF FF 00 00 00 00 00 00 A8 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 
	// 1000010 05 A8 00 00 0A D8 9C 20 00 00 10 03 4F 54 00 00 00 00 00 00 00 00 00 00 00 04 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 0C 00 01 77 00 00 D8 06 1F 19 00 1B CD FF 1E 1E 1F 2A 0C 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 14 06 0C 00 00 77 00 00 00 03 03 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 77 0B 20 01 5A 0B 30 00 03 03 00 00 E5 4E C0 19 00 00 B5 55 3C 0E 10 01 02 19 00 00 00 00 01 00 00 E0 04 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 FF 00 00 00 00 00 00 33 41 01 00 00 00 F2 A8 00 00 00 00 92 00 FF FF FF FF FF FF 00 00 00 00 00 00 A8 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 
	//
	private static final String MEMORY_SNAPSHOTS_FILE_PATH = WORKING_DIR + "BiscuitDump.txt";
	
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
		File f = new File(CONFIG_FILE_PATH);
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
			replay_file = new File(REPLAY_FILE_PATH);
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
			banxian_dump = new File(DISASSEMBLY_LOG_FILE_PATH);
			fr = new FileReader(banxian_dump);
			br = new BufferedReader(fr, 100);
			
			banxian_biscuit = new File(MEMORY_SNAPSHOTS_FILE_PATH);
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
