package org.quadpixels;

public class EmulatorThread extends Thread {
	private CPU cpu;
	private FleurDeLisDriver theFleurDeLisDriver;
	int deadlockCounter = 0;
	private static final int tommy_batch = (1<<19)-1;
	Class1 host;
	
	// Must construct CPU and Driver first then construct this thread.
	public EmulatorThread(CPU _cpu, FleurDeLisDriver _drv, Class1 _host) {
		assert(cpu != null && _drv != null);
		cpu = _cpu;
		theFleurDeLisDriver = _drv;
		host = _host;
	}
	
	@Override
	public	void run() {
		// 11-16 Still under construction.
		{
			TommyHelper.openBanxianDump();
			int batch_size = 100000;
			int curr_batch_todo = batch_size;
			byte lcdbuffer[] = new byte[1600];
			try {
				while(true) {
					// Execute a BATCH of insts, then update LCD!
					while(curr_batch_todo > 0) {
					
						// ################################
						// FOR DEVELOPMENT! Uncomment to
						// enable trace comparison.
						// ################################
						
						/*
						if(cpu.total_inst_count > TommyHelper.NUM_INSTS+TommyHelper.NUM_INSTS_SKIP) 
						{
							throw new Exception("[XD] Completed following trace file!!");
						}*/
						

						if((cpu.total_inst_count%TommyHelper.MEMORY_SNAPSHOT_INTERVAL)==0) {
							theFleurDeLisDriver.tommyBiscuit();
						}
						
						// 0.5: Replay
						if(cpu.total_inst_count == TommyHelper.next_reply_tick) {
							if(TommyHelper.replay_idx > 0) {
								int idx = TommyHelper.replay_idx-1;
								byte kc = TommyHelper.replay_keycodes[idx];
								boolean is_down = TommyHelper.replay_updowns[idx];
								int row = kc>>3;
								int col = kc&7;
								if(is_down) {
									System.out.println("Pressed key "+(int)kc);
									theFleurDeLisDriver.keypadmatrix[row][col] = 1;
								} else {
									System.out.println("Released key "+(int)kc);
									theFleurDeLisDriver.keypadmatrix[row][col] = 0;
								}
								theFleurDeLisDriver.updateKeypadRegisters();
							}
							
							if(TommyHelper.replay_idx < TommyHelper.num_replay_actions) {
								TommyHelper.next_reply_tick += TommyHelper.replay_delays[TommyHelper.replay_idx];
								TommyHelper.replay_idx++;
								System.out.println("Nexst replay action at "+TommyHelper.next_reply_tick);
							}
						}
						
						// 1. We need to set the NMI flag -- heartbeat signal
						if((cpu.total_inst_count & tommy_batch) == tommy_batch ) {
							theFleurDeLisDriver.threadFlags |= 0x08; // NMI flag
						}
						
						// 2. Process NMI and IRQ
						if((theFleurDeLisDriver.threadFlags & 0x08)!=0) {
							theFleurDeLisDriver.threadFlags &= 0xFFF7; // Removed!
							cpu.nmi = false;
							deadlockCounter--;
						} else if(((cpu.regs.ps & 0x4)==0) && 
								((theFleurDeLisDriver.threadFlags & 0x10)!=0)) {
							theFleurDeLisDriver.threadFlags &= 0xFFEF;
							cpu.irq = false;
							deadlockCounter--;
						}
						
						deadlockCounter++;
						
						
						// 3. Dead lock handler
						//  (Is this called a Watchdog ?)
						if(deadlockCounter==3000) {
							deadlockCounter = 0;
							if((theFleurDeLisDriver.threadFlags&0x80)==0) {
								theFleurDeLisDriver.checkTimebaseAndEnableIRQnEXIE1();
								if(theFleurDeLisDriver.timer0started) {
									theFleurDeLisDriver.prevtimer0value += 3;
									if(theFleurDeLisDriver.prevtimer0value >= 0xFF) {
										theFleurDeLisDriver.prevtimer0value = 0;
										theFleurDeLisDriver.turnOff2HzNMIMaskAddIRQFlag();
									}
								} 
							} else { // RESET.
								theFleurDeLisDriver.resetCPU();
							}
						} else {
							if(theFleurDeLisDriver.timer0started) {
								theFleurDeLisDriver.prevtimer0value += 3;
								if(theFleurDeLisDriver.prevtimer0value >= 0xFF) {
									theFleurDeLisDriver.prevtimer0value = 0;
									theFleurDeLisDriver.turnOff2HzNMIMaskAddIRQFlag();
								}
							}
						}
						
						int cycles = cpu.oneInstruction();
						curr_batch_todo -= cycles;
					}
					// End batch, re-charge this batch!!!!
					
					curr_batch_todo = batch_size;
					
					// Update LCD. How to do that?
					for(int i=0; i<1600; i++) {
						lcdbuffer[i] = theFleurDeLisDriver.getByte(0x9C0+i);
					}
					host.updateLCD(lcdbuffer, (int) cpu.total_inst_count);
				}
			} catch (Exception e) {
				e.printStackTrace();
				this.stop();
			} finally {
				System.out.println("##### Trace file checking status: ######");
				TommyHelper.reportIfTraceFileConsumed();
			}
		}
	}
}
