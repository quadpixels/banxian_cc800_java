// Copying BanXian's 6502 Simulator ...
// Progressing very very slowly!

package org.quadpixels;

import java.io.File;


import java.io.FileInputStream;
import java.io.IOException;
import java.awt.*;
import java.awt.event.*;
import java.util.HashMap;

public class Class1 extends Frame {
	FleurDeLisDriver fleurDeLisDriver;
	private static HashMap<Integer, Integer> keyCodeToMatrixPos = new HashMap<Integer, Integer>();
	static {
		// 0 to 7
		keyCodeToMatrixPos.put(KeyEvent.VK_F12, 2); // On-off
		
		// 8 to 15
		keyCodeToMatrixPos.put(KeyEvent.VK_F5, 8);
		keyCodeToMatrixPos.put(KeyEvent.VK_F6, 9);
		keyCodeToMatrixPos.put(KeyEvent.VK_F7, 10);
		keyCodeToMatrixPos.put(KeyEvent.VK_F8, 11);
		keyCodeToMatrixPos.put(KeyEvent.VK_F9, 12);
		keyCodeToMatrixPos.put(KeyEvent.VK_F10, 13);
		keyCodeToMatrixPos.put(KeyEvent.VK_F11, 14);
		
		// 16 to 23
		keyCodeToMatrixPos.put(KeyEvent.VK_CONTROL, 16);
		keyCodeToMatrixPos.put(KeyEvent.VK_SHIFT, 17);
		keyCodeToMatrixPos.put(KeyEvent.VK_CAPS_LOCK, 18);
		keyCodeToMatrixPos.put(KeyEvent.VK_ESCAPE, 19);
		keyCodeToMatrixPos.put(KeyEvent.VK_0, 20);
		keyCodeToMatrixPos.put(KeyEvent.VK_PERIOD, 21);
		keyCodeToMatrixPos.put(KeyEvent.VK_EQUALS, 22);
		keyCodeToMatrixPos.put(KeyEvent.VK_LEFT, 23);
		
		// 24 to 31
		keyCodeToMatrixPos.put(KeyEvent.VK_Z, 24);
		keyCodeToMatrixPos.put(KeyEvent.VK_X, 25);
		keyCodeToMatrixPos.put(KeyEvent.VK_C, 26);
		keyCodeToMatrixPos.put(KeyEvent.VK_V, 27);
		keyCodeToMatrixPos.put(KeyEvent.VK_B, 28);
		keyCodeToMatrixPos.put(KeyEvent.VK_N, 29);
		keyCodeToMatrixPos.put(KeyEvent.VK_M, 30);
		keyCodeToMatrixPos.put(KeyEvent.VK_PAGE_UP, 31);
		
		// 32 to 39
		keyCodeToMatrixPos.put(KeyEvent.VK_A, 32);
		keyCodeToMatrixPos.put(KeyEvent.VK_S, 33);
		keyCodeToMatrixPos.put(KeyEvent.VK_D, 34);
		keyCodeToMatrixPos.put(KeyEvent.VK_F, 35);
		keyCodeToMatrixPos.put(KeyEvent.VK_G, 36);
		keyCodeToMatrixPos.put(KeyEvent.VK_H, 37);
		keyCodeToMatrixPos.put(KeyEvent.VK_J, 38);
		keyCodeToMatrixPos.put(KeyEvent.VK_K, 39);
		
		// 40 to 47
		keyCodeToMatrixPos.put(KeyEvent.VK_Q, 40);
		keyCodeToMatrixPos.put(KeyEvent.VK_W, 41);
		keyCodeToMatrixPos.put(KeyEvent.VK_E, 42);
		keyCodeToMatrixPos.put(KeyEvent.VK_R, 43);
		keyCodeToMatrixPos.put(KeyEvent.VK_T, 44);
		keyCodeToMatrixPos.put(KeyEvent.VK_Y, 45);
		keyCodeToMatrixPos.put(KeyEvent.VK_U, 46);
		keyCodeToMatrixPos.put(KeyEvent.VK_I, 47);
		
		// 48 to 55
		keyCodeToMatrixPos.put(KeyEvent.VK_O, 48);
		keyCodeToMatrixPos.put(KeyEvent.VK_L, 49);
		keyCodeToMatrixPos.put(KeyEvent.VK_UP, 50);
		keyCodeToMatrixPos.put(KeyEvent.VK_DOWN, 51);
		keyCodeToMatrixPos.put(KeyEvent.VK_P, 52);
		keyCodeToMatrixPos.put(KeyEvent.VK_ENTER, 53);
		keyCodeToMatrixPos.put(KeyEvent.VK_PAGE_DOWN, 54);
		keyCodeToMatrixPos.put(KeyEvent.VK_RIGHT, 55);
		
		// 56 to 63
		keyCodeToMatrixPos.put(KeyEvent.VK_F1, 58);
		keyCodeToMatrixPos.put(KeyEvent.VK_F2, 59);
		keyCodeToMatrixPos.put(KeyEvent.VK_F3, 60);
		keyCodeToMatrixPos.put(KeyEvent.VK_F4, 61);
	}
	String[] this_args;
	Panel hello;
	public Class1() {
		setTitle("CC800 Stolen from Banxian");
		setSize(650, 680);
		hello = new Panel();
		add("Center", hello);
		final Button button = new Button("Launch CC800");
		add("South", button);
		button.addActionListener(new ActionListener() {
		    public void actionPerformed(ActionEvent e) {
		    	WQCCC800(this_args);
		    	button.setEnabled(false);
		    }
		});
		

		final Button button2 = new Button("X");
		add("East", button2);
		button2.addActionListener(new ActionListener() {
		    public void actionPerformed(ActionEvent e) {
		    	System.exit(0);
		    }
		});

		setVisible(true);
		
		System.out.println("Adding key listener.");
		
		hello.addKeyListener(new java.awt.event.KeyAdapter() {
			
			private void do_keyPressed(int vk_code, boolean is_pressed) {
				if(keyCodeToMatrixPos.containsKey((Integer)vk_code)) {
					int wqx_keycode = keyCodeToMatrixPos.get((Integer)vk_code);
					int row = wqx_keycode >> 3;
					int col = wqx_keycode & 0x07;
					fleurDeLisDriver.keymatrixChange(row, col, is_pressed);
				}
			}
			
			public void keyPressed(java.awt.event.KeyEvent evt) {
				System.out.println("[KeyPressed] "+evt.toString());
				do_keyPressed(evt.getKeyCode(), true);
			}
			
			public void keyReleased(java.awt.event.KeyEvent evt) {
				System.out.println("[KeyRelease] "+evt.toString());
				do_keyPressed(evt.getKeyCode(), false);
			}
		});
		

		TommyHelper.openReplay();
	}

	public void redrawPanelString(String s) {
		int width = hello.getWidth();
		int height = hello.getHeight();
		Graphics g = hello.getGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, width, height);
		g.setColor(Color.BLACK);
		g.drawString(s, width/2 - 25, height/2);
	}
	
	public void updateLCD(byte[] ch, int inst_cnt) { // Length = 160 / 8 * 80 = 1600 bytes 
		int idx = 0, i = 0, j = 0;
		Graphics g = hello.getGraphics();
		boolean curr_black = false;
		while(idx < 1600) {
			for(int bid=0; bid<8; bid++) { // bid = bit id
				byte mask = (byte)(1 << (7-bid));
				if((ch[idx] & mask)!=0) {
					curr_black = true;
				} else curr_black = false;
				
				if(curr_black==true) {
					g.setColor(Color.BLACK);
				} else g.setColor(Color.LIGHT_GRAY);

				g.fillRect(i*2, j*2, 2, 2);
				i=i+1;
				if(i==160) { i=0; j+=1; }
			}
			idx++;
		}
		g.setColor(Color.GREEN);
		g.fillRect(320, 0, 320, 80);
		g.setColor(Color.WHITE);
		g.drawString(""+inst_cnt, 320, 15);
	}
	
	public void WQCCC800(String[] args) {
		System.out.println("Hello world. Number of arguments="
				+ args.length);
		
		TommyHelper.syncWithSim800();
		
		// Current Working Directory.
		{
			File directory = new File(".");
			try {
				System.out.println("CWD=" + directory.getCanonicalPath());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		byte[] brom_buf = null;
		// Open a file and load into memory.
		{
			try {
				File brom_file = new File("obj.bin");
				FileInputStream fis = new FileInputStream(brom_file);
				int brom_available = fis.available();
				System.out.println("File obj.bin available=" + brom_available);
				brom_buf = new byte[brom_available];
				// Butterfly it by 0x4000 bytes!
				int idx = 0;
				while(fis.available() > 0) {
					fis.read(brom_buf, idx+0x4000, 0x4000);
					fis.read(brom_buf, idx, 0x4000);
					idx += 0x8000;
				}
				fis.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		byte[] norflash_buf = null;
		{
			try {
				File norflash_file = new File("cc800.fls");
				FileInputStream fis = new FileInputStream(norflash_file);
				int norflash_available = fis.available();
				System.out.println("File cc800.fls available=" + norflash_available);
				norflash_buf = new byte[norflash_available];
				// Butterfly this by 0x4000 bytes!
				int idx = 0;
				while(fis.available() > 0) {
					fis.read(norflash_buf, idx+0x4000, 0x4000);
					fis.read(norflash_buf, idx, 0x4000);
					idx += 0x8000;
				}
				fis.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		CPU cpu;
		{
			cpu = new CPU();
			TommyHelper.cpu = cpu;
		}
		
		fleurDeLisDriver = new FleurDeLisDriver(brom_buf, norflash_buf, cpu);
		fleurDeLisDriver.tommyDumpMemory();
		cpu.cpuInitialize();
		EmulatorThread thd = new EmulatorThread(cpu, fleurDeLisDriver, this);
		thd.start();
	}
	
	public static void main(String[] args) {
		Class1 class1 = new Class1();
		class1.this_args = args;
	}
}
