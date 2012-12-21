/** Update log goes here!
 * Nov 22: Ah! I have some interrupts!
 *   10:40 PM Start to implement interrupts.
 */

package org.quadpixels;

import org.quadpixels.FleurDeLisDriver.BasicAddress;
import org.quadpixels.FleurDeLisDriver.IORead;
import org.quadpixels.FleurDeLisDriver.IOWrite;

public class CPU {
	public long total_inst_count = 0;
	
	private static final int IO_RANGE = 0x40;
	public FleurDeLisDriver theFleurDeLisDriver;
	public class Regs {
		// Why, Java doesn't support unsigned data types?
		byte a;
		public byte getA() { return a; }
		public void setA(byte _a) { a = _a; } 
		
		private byte x;
		public byte getX() { return x; }
		
		private byte y;
		public byte getY() { return y; }
		
		byte ps;
		public byte getPS() { return ps; }
		public void setPS(byte b) { ps = b; }
		
		short pc; // Program Counter.
		public short getPC() { return pc; }
		
		private short sp; // Stack pointer.
		public short getSP() { return sp; }
	}
	
	public Regs regs = new Regs();
	public FleurDeLisDriver mem;
	public boolean irq, nmi, wai, stp;
	
	public void cpuInitialize() {
		regs.a = 0; regs.x = 0; regs.y = 0; regs.ps = 0x24;
		regs.pc = theFleurDeLisDriver.getWord(0xFFFC);
		regs.sp = 0x01FF;
		irq = true; nmi = true; wai = false; stp = false;
	}
	
	//   N V 1 B D I Z C   <-- 6502 registers
	//  |7|6|5|4|3|2|1|0   
	private final static byte AF_SIGN = (byte)0x80;
	private final static byte AF_OVERFLOW = (byte)0x40;
	private final static byte AF_RESERVED = (byte)0x20;
	private final static byte AF_BREAK = (byte)0x10;
	private final static byte AF_DECIMAL = (byte)0x08;
	private final static byte AF_INTERRUPT = (byte)0x04;
	private final static byte AF_ZERO = (byte)0x02;
	private final static byte AF_CARRY = (byte)0x01;
	
	// For temporary use!
	private boolean flag_c, flag_n, flag_v, flag_z;
	private int pc,   // Address of instruction
				addr,   // Address of source operand 
				cycles,
				val,
				temp; //
	
	// #######################################
	// Private methods for logging disassembly
	private String regs_log, inst_log, operand_log, machcode_log;
	private short prev_pc;
	private void logRegisters() {
		String cpu_state = Integer.toBinaryString(regs.ps & 0x000000FF);
		StringBuilder sb = new StringBuilder(cpu_state);
		for(int i=cpu_state.length(); i<8; i++) { sb.insert(0, "0"); }
		regs_log = (String.format("%02X %02X %02X %04X %s",
				regs.a,
				regs.x,
				regs.y,
				regs.sp,
				sb.toString()));
	}
	// This method is for debugging purpose only.
	private void logOperandAndMachineCode() {
		StringBuilder sb_operand = new StringBuilder();
		StringBuilder sb_machcode = new StringBuilder();
		/*
		 *  3-byte inst in memory:
		 *  +--------+----+----+
		 *  | OPCode | Hi | Lo |
		 *  +--------+----+----+
		 *  p         p+1  p+2
		 */
		// 1. Log the operand.
		{
			int p = prev_pc + 1;
			if(this_addr_mode == null) operand_log="";
			else {
				if(this_addr_mode instanceof ABS ||  // The beauty and ugliness of
					this_addr_mode instanceof ZPG) { // O.O.P. !!!
					while((short)(p&0xFFFF) != regs.pc) {
						int addr = ((int)(p)) & 0xFFFF;
						sb_operand.insert(0, String.format("%02X", theFleurDeLisDriver.getByte(addr)));
						p += 1;
					}
					sb_operand.insert(0, "$");
				} 
				else if(this_addr_mode instanceof IMM) {
					sb_operand.append("#$");
					int addr = ((int)(p)) & 0xFFFF;
					sb_operand.append(String.format("%02X", theFleurDeLisDriver.getByte(addr)));
				}
				else if(this_addr_mode instanceof REL) {
					sb_operand.append("$");
					// PC has NOT been incremented by calling exec() yet,
					//    that being said, logOperand() must precede exec()!!!\
					int pc = ((int)p) & 0x0000FFFF;
					int addr = (int)(char)theFleurDeLisDriver.getByte(pc);
					sb_operand.append(String.format("%04X", (regs.pc + addr)&0x0000FFFF));
				}
				else if(this_addr_mode instanceof IABS) { // JMP $(00BA)
					sb_operand.append("$(");
					int pc = ((int)p) & 0x0000FFFF;
					int addr = (int)(theFleurDeLisDriver.getWord(pc&0xFFFF)&0xFFFF);
					sb_operand.append(String.format("$04X)", addr));
				}
				else if(this_addr_mode instanceof INDY) { // STA ($08),Y
					sb_operand.append("($");
					int pc = ((int)p) & 0x0000FFFF;
					int addr = (int)(char)theFleurDeLisDriver.getByte(pc) & 0xFF;
					sb_operand.append(String.format("%02X),Y", addr));
				}
				else if(this_addr_mode instanceof ABSX) { // STA $9999, X
					sb_operand.append("$");
					int addr = (int)(short)theFleurDeLisDriver.getWord(regs.pc&0xFFFF) & 0xFFFF;
					sb_operand.append(String.format("%04X,X", addr));
				}
				else if(this_addr_mode instanceof ABSY) { // STA $9999, X
					sb_operand.append("$");
					int addr = (int)(short)theFleurDeLisDriver.getWord(p&0xFFFF) & 0xFFFF;
					sb_operand.append(String.format("%04X,Y", addr));
				}
				else if(this_addr_mode instanceof ZPGX) { // STA $70,X
					sb_operand.append("$");
					int addr = (int)(char)theFleurDeLisDriver.getByte(p&0xFFFF)&0xFF;
					sb_operand.append(String.format("%02X,X", addr));
				}
				operand_log = sb_operand.toString();
			}
		}
		// 2. Log the machine codes
		{
			int p = prev_pc;
			while((short)(p&0xFFFF) != regs.pc) {
				sb_machcode.append(String.format("%02X", theFleurDeLisDriver.getByte(p&0xFFFF)));
				p=p+1;
			}
			machcode_log = sb_machcode.toString();
		}
	}
	private void logInst() {
		StringBuilder sb = new StringBuilder();
		String inst_name = this_inst.getClass().getName();
		int idx = inst_name.lastIndexOf('$');
		inst_name = inst_name.substring(idx+1);
		sb.append(inst_name + " ");
		inst_log = sb.toString();
	}
	
	// Caution: dependencies of those macros.
	
	// ###################
	// Read, write, incrementing PC, etc
	// ###################
	final class CYC { void foo(int _cycles) { cycles += _cycles; }}
	final CYC cyc = new CYC();
	
	final class READ {
		byte foo() {
			if((addr&0x0000FFFF) < IO_RANGE) {
				try {
					IORead ior = (IORead) theFleurDeLisDriver.ioread[addr];
					return ior.foo(addr);
				} catch (NullPointerException e) {
					System.err.println(String.format("IO Read ADDR=%08X", addr));
					throw e;
				}
			} else {
				return theFleurDeLisDriver.getByte(addr&0x0000FFFF);
			}
		}
	}
	final READ read = new READ();
	
	final class WRITE {
		void foo(byte data) {
			if((addr&0x0000FFFF) < IO_RANGE) {
				try {
					if((addr&0xFFFF)==0) {
						TommyHelper.DBG("Executed="+total_inst_count);
					}
					IOWrite iow = (IOWrite) theFleurDeLisDriver.iowrite[addr&0x0000FFFF];
					iow.foo(addr&0x0000FFFF, data);
				} catch (NullPointerException e) {
					System.err.println(String.format("IO Write ADDR=%08X @ Inst %d", addr,
							total_inst_count));
				}
			} else {
				theFleurDeLisDriver.writeByte(addr&0x0000FFFF, data);
			}
		}
	}
	final WRITE write = new WRITE();
	
	final class PUSH {
		void foo(byte data) {
			int addr = regs.sp & 0x000001FF;
			theFleurDeLisDriver.writeByte(addr, data);
			addr = addr - 1;
			if(addr < 0x0100) addr = 0x01FF;
			regs.sp = (short)addr;
		}
	} final PUSH push = new PUSH();
	
	final class POP {
		byte foo() {
			int addr = ((int)regs.sp) & 0x000001FF /* + 1 */; // Pay attention to opr priority.
			addr = addr + 1;
			if(addr > 0x01FF) addr = 0x0100;
			byte ret = theFleurDeLisDriver.getByte(addr);
			regs.sp = (short)addr;
			return ret;
		}
	} final POP pop = new POP();
	
	// ###################################
	// Flags
	// ###################################
	final class AF_TO_EF {
		void foo() {
			flag_c = ((regs.ps & AF_CARRY)==AF_CARRY);
			flag_n = ((regs.ps & AF_SIGN)==AF_SIGN);
			flag_v = ((regs.ps & AF_OVERFLOW)==AF_OVERFLOW);
			flag_z = ((regs.ps & AF_ZERO) == AF_ZERO);
		}
	}
	final AF_TO_EF af_to_ef = new AF_TO_EF();
	
	final class EF_TO_AF {
		void foo() {
			regs.ps = (byte) (regs.ps & ~(AF_CARRY | AF_SIGN | AF_OVERFLOW 
					| AF_ZERO));
			if(flag_c == true) regs.ps |= AF_CARRY;
			if(flag_n == true) regs.ps |= AF_SIGN;
			if(flag_v == true) regs.ps |= AF_OVERFLOW;
			if(flag_z == true) regs.ps |= AF_ZERO;
		}
	}
	final EF_TO_AF ef_to_af = new EF_TO_AF();
	
	final class TOBIN {
		byte foo(byte b) {
			return (byte)(((b) >> 4)*10 + ((b) & 0x0F));
		}
	} final TOBIN to_bin = new TOBIN();
	
	final class TOBCD {
		byte foo(byte b) {
			return (byte)(((((b)/10) % 10) << 4) | ((b) % 10));
		}
	} final TOBCD to_bcd = new TOBCD();
	
	final class SETNZ {
		void foo(byte a) {
			flag_n = ((a & (byte)0x80) == (byte)0x80);
			flag_z = (a == 0x00);
		}
	}
	final SETNZ setnz = new SETNZ();
	
	
	// #################
	// Addressing modes
	// #################
	interface AddressMode { abstract void foo(); }
	private AddressMode this_addr_mode;
	// the "am" method just works as a driver, and it's pretty handy to hook a
	//  debugging stmt here.
	private void am(AddressMode _am) {
		this_addr_mode = _am; 
		if(_am != null) {_am.foo();}
		if(TommyHelper.IS_LOG_MEM == 1 && total_inst_count > TommyHelper.NUM_INSTS_SKIP)
			logOperandAndMachineCode();
	}
	final class ABS implements AddressMode {
		public void foo() {
			pc = ((int)regs.pc) & 0x0000FFFF;
			addr = theFleurDeLisDriver.getWord(pc);
			regs.pc = (short)(pc+2);
		}
	}
	final ABS abs = new ABS();
	
	final class ABSX implements AddressMode {
		public void foo() {
			addr = theFleurDeLisDriver.getWord(regs.pc & 0xFFFF);
			addr += (short)(regs.x & 0xFF);
			regs.pc += 2;
		}
	} final ABSX absx = new ABSX();
	
	final class ABSY implements AddressMode {
		public void foo() {
			addr = theFleurDeLisDriver.getWord(regs.pc & 0xFFFF);
			addr += (short)(regs.y & 0xFF);
			regs.pc += 2;
		}
	} final ABSY absy = new ABSY();
	
	final class IABS implements AddressMode {
		public void foo() {
			int star_pc = theFleurDeLisDriver.getWord(regs.pc & 0xFFFF);
			addr = theFleurDeLisDriver.getWord(star_pc & 0xFFFF);
			regs.pc += 2;
		} 
	} final IABS iabs = new IABS();
	
	final class INDX implements AddressMode { // LDA ($F0,X)
		public void foo() {
			int k = (int)(theFleurDeLisDriver.getByte(regs.pc&0xFFFF)&0xFF
					+ (int)(regs.x&0xFF))&0xFFFF;
			addr = theFleurDeLisDriver.getWord(k);
			regs.pc++;
		}
	} final INDX indx = new INDX();
	
	final class INDY implements AddressMode {
		public void foo() {
			int k = ((int)(regs.pc)) & 0x0000FFFF;
			
			addr = theFleurDeLisDriver.getWord(
					theFleurDeLisDriver.getByte(k)&0x000000FF)
					+ (short)(regs.y & 0xFF);
			addr = addr & 0x0000FFFF;
			regs.pc++;
		}
	} final INDY indy = new INDY();
	
	final class ZPG implements AddressMode {
		public void foo() {
			int pc = ((int)regs.pc) & 0x0000FFFF;
			addr = theFleurDeLisDriver.getByte(pc);
			addr &= 0x000000FF; // Zero page, addr should be 00 to FF /* &=0000FFFF */
			regs.pc += 1;
		}
	}
	final ZPG zpg = new ZPG();
	
	final class ZPGX implements AddressMode {
		public void foo() {
			int reg_plus_x = (regs.pc&0xFFFF);
			addr = theFleurDeLisDriver.getByte(reg_plus_x&0xFFFF) + (regs.x&0xFF);
			addr &= 0xFF;
			regs.pc+=1;
		}
	} final ZPGX zpgx = new ZPGX();
	
	final class ZPGY implements AddressMode {
		public void foo() {
			int reg_plus_y = regs.pc&0xFFFF;
			addr = theFleurDeLisDriver.getByte(reg_plus_y) + (regs.y&0xFF);
			addr &= 0xFF;
			regs.pc+=1;
		}
	} final ZPGY zpgy = new ZPGY();
	
	final class REL implements AddressMode {
		public void foo() {
			pc = ((int)regs.pc) & 0x0000FFFF;
			addr = (int)(char)theFleurDeLisDriver.getByte(pc);
			addr &= 0x0000FFFF;
			regs.pc = (short)(pc+1);
		}
	} final REL rel = new REL();
	
	final class IMM implements AddressMode { 
		public void foo(){addr = ((int)regs.pc++) & 0x0000FFFF;} }
	final IMM imm = new IMM();
	
	
	// #################
	// Instructions
	// #################
	interface InstName { abstract void foo(); }
	private InstName this_inst;
	void exec(InstName inst) {
		this_inst = inst;
		inst.foo();
	}
	
	final private class ADC implements InstName { public void foo() {
			temp = (read.foo()) & 0x000000FF; 
			if((regs.ps & AF_DECIMAL)!=0) {
				val = to_bin.foo(regs.a) + to_bin.foo((byte)temp)
						+ (flag_c != false ? 1 : 0);
				flag_c = (val > 99);
				regs.a = to_bcd.foo((byte)val); cyc.foo(1);
				setnz.foo(regs.a);
			} else {
				val = (regs.a&0xFF) + temp + (flag_c != false?1:0);
				flag_c = (val>0xFF);
				flag_v = (((regs.a & 0x80) == (temp &0x80)) &&
						((regs.a & 0x80)!=(val&0x80)));
				regs.a = (byte)(val & 0xFF);
				setnz.foo(regs.a);
			}
		}
	} final ADC adc = new ADC();
	
	final private class BCC implements InstName { public void foo() {
		if(flag_c == false) { regs.pc += addr; cyc.foo(1); } }
	} final BCC bcc = new BCC();
	
	final private class AND implements InstName { public void foo() {
		regs.a &= read.foo(); setnz.foo(regs.a); }
	} final AND and = new AND();
	
	final private class ASL implements InstName { public void foo() {
			val = (read.foo() & 0x000000FF) << 1;
			flag_c = (val > 0x000000FF);
			setnz.foo((byte)val);
			write.foo((byte)val);
		}
	} final ASL asl = new ASL();
	
	final private class ASLA implements InstName { public void foo() {
			val = (regs.a & 0x000000FF) << 1;
			flag_c = (val > 0xFF); setnz.foo((byte)val);
			regs.a = (byte)val;
		}
	} final ASLA asla = new ASLA();
	
	final private class BCS implements InstName { public void foo() {
		if(flag_c == true) {regs.pc += addr; cyc.foo(1); }}
	}final BCS bcs = new BCS();
	
	final private class BEQ implements InstName { public void foo() { 
		if(flag_z==true) {regs.pc += addr; cyc.foo(1);} } }
	final BEQ beq = new BEQ();
	
	final private class BIT implements InstName { public void foo() {
			val = read.foo() & 0x000000FF;
			flag_z = !((regs.a & val)!=0);
			flag_n = ((val & 0x80)!=0);
			flag_v = ((val & 0x40)!=0);
		}
	} final BIT bit = new BIT();
	
	final private class BMI implements InstName { public void foo() {
		if(flag_n==true) {regs.pc += addr; cyc.foo(1); }}
	} final BMI bmi = new BMI();
	
	final private class BPL implements InstName { public void foo() {
		if(flag_n == false) { regs.pc += addr; cyc.foo(1); } }
	} final BPL bpl = new BPL();
	
	final private class BRK implements InstName { public void foo() {
			regs.pc += 1;
			push.foo((byte)(regs.pc >> 8));
			push.foo((byte)(regs.pc & 0xFF));
			ef_to_af.foo();
			regs.ps |= AF_BREAK;
			push.foo(regs.ps);
			regs.ps |= AF_INTERRUPT;
			regs.pc = theFleurDeLisDriver.getWord(0xFFFE);
		}
	} final BRK brk = new BRK();
	
	final private class BNE implements InstName { public void foo () {
		if(flag_z == false) {regs.pc += addr; cyc.foo(1); } }
	} final BNE bne = new BNE();
	
	final private class BVS implements InstName { public void foo() {
		if(flag_v==true) { regs.pc += addr; cyc.foo(1); } }
	} final BVS bvs = new BVS();
	
	final private class CLC implements InstName { public void foo() {
		flag_c = false;
		regs.ps &= 0xFE; }
	} final CLC clc = new CLC();
	
	final private class CLI implements InstName { public void foo() {
		regs.ps &= ~AF_INTERRUPT; }
	} final CLI cli = new CLI();
	
	final private class CMP implements InstName { public void foo() {
		val = read.foo(); 
		int lhs = (regs.a & 0x000000FF); // regs.a is a BYTE
		int rhs = (val    & 0x000000FF); // val is a WORD.  Both are unsigned.
		flag_c = (lhs >= rhs); 
		val = lhs - rhs;
		setnz.foo((byte)val);
		}
	} final CMP cmp = new CMP();
	
	final private class CPX implements InstName { public void foo() {
		val = read.foo() & 0xFF;
		flag_c = ((regs.x & 0xFF) >= val);
		val = ((regs.x & 0xFF) - (val & 0xFF));
		setnz.foo((byte)val); }
	}final CPX cpx = new CPX();
	
	final private class CPY implements InstName { public void foo() {
		val = read.foo() & 0xFF;
		flag_c = ((regs.y & 0xFF) >= val);
		val = ((regs.y & 0xFF) - (val & 0xFF));
		setnz.foo((byte)val); }
	}final CPY cpy = new CPY();
	
	final private class DEC implements InstName { public void foo() {
			val = read.foo() - 1;
			setnz.foo((byte)val);
			write.foo((byte)val);
		}
	} final DEC dec = new DEC();
	
	final private class DEX implements InstName { public void foo() {
		regs.x -= 1; setnz.foo(regs.x); }
	} final DEX dex = new DEX();
	
	final private class DEY implements InstName { public void foo() {
		regs.y -= 1; setnz.foo(regs.y); }
	} final DEY dey = new DEY();
	
	final private class EOR implements InstName { public void foo() {
			regs.a ^= ((byte)read.foo());
			setnz.foo(regs.a);
		}
	} final EOR eor = new EOR();
	
	final private class INC implements InstName { public void foo() {
		val = read.foo() + 1;
		setnz.foo((byte)val);
		write.foo((byte)val); }
	} final INC inc = new INC();
	
	final private class INX implements InstName { public void foo() {
			regs.x += 1; setnz.foo(regs.x);
		}
	} final INX inx = new INX();
	
	final private class INY implements InstName { public void foo() {
		regs.y += 1; setnz.foo(regs.y); }
	} final INY iny = new INY();
	
	
	
	final private class JMP implements InstName { public void foo() { 
		regs.pc = (short)addr; }}
	final JMP jmp = new JMP();
	
	final private class JSR implements InstName { public void foo() {
		regs.pc -= 1;
		push.foo((byte) (regs.pc >> 8));
		push.foo((byte) (regs.pc & 0xFF));
		regs.pc = (short)addr;
		}
	} final JSR jsr = new JSR();
	
	final private class LDA implements InstName { public void foo() {
		regs.a = read.foo();
		setnz.foo(regs.a);
		}
	}
	final LDA lda = new LDA();
	
	private void lda() {
		regs.a = read.foo();
		setnz.foo(regs.a);
		this_inst = lda;
	}
	
	private void bvc() {
		if(flag_v==false) regs.pc += addr; cyc.foo(1);
	}
	
	private void clv() { flag_v=false; }
	
	final private class LDX implements InstName { public void foo() {regs.x = read.foo();
			setnz.foo(regs.x); }}
	final LDX ldx = new LDX();
	
	final private class LDY implements InstName { public void foo() {regs.y = read.foo();
	setnz.foo(regs.y); }}
	final LDY ldy = new LDY();
	
	final private class LSR implements InstName { public void foo() {
			val = read.foo()&0xFF;
			flag_c = ((val&1)!=0);
			flag_n = false;
			val >>= 1;
			flag_z = ((val&0xFF)==0);
			write.foo((byte)val);
		}
	} final LSR lsr = new LSR();
	
	final private class LSRA implements InstName { public void foo() {
			flag_c = ((regs.a & 1)!=0);
			flag_n = false;
			regs.a = (byte)((regs.a&0xFF)>>1);
			flag_z = ((regs.a&0xFF)==0);
		}
	} final LSRA lsra = new LSRA();
	
	final private class NOP implements InstName { public void foo() {} }
	final NOP nop = new NOP();
	
	final private class ORA implements InstName { public void foo() { regs.a |= read.foo();
		setnz.foo(regs.a);}
	} final ORA ora = new ORA();
	
	final private class PHA implements InstName { public void foo() {
		push.foo(regs.a); }
	} final PHA pha = new PHA();
	
	final private class PLA implements InstName { public void foo() {
		regs.a = pop.foo(); setnz.foo(regs.a); }
	} final PLA pla = new PLA();
	
	final private class PLP implements InstName { public void foo() {
		regs.ps = pop.foo(); af_to_ef.foo(); }
	} final PLP plp = new PLP();
	
	final private class PHP implements InstName { public void foo() {
		ef_to_af.foo();
		regs.ps |= AF_RESERVED;
		push.foo(regs.ps);
		}
	} final PHP php = new PHP();
	
	final private class ROLA implements InstName { public void foo() {
			val = regs.a << 1 | (flag_c!=false?1:0);
			val &= 0xFFFF;
			flag_c = (val > 0xFF);
			regs.a = (byte)(val&0xFF);
			setnz.foo(regs.a);
		}
	} final ROLA rola = new ROLA();
	
	final private class ROL implements InstName { public void foo() {
			temp = read.foo() & 0xFF;
			val = (temp << 1) | (flag_c != false?1:0);
			flag_c = (val > 0xFF);
			setnz.foo((byte)val);
			write.foo((byte)val);
		}
	} final ROL rol = new ROL();
	
	final private class ROR implements InstName { public void foo() {
			temp = (read.foo()&0xFF);
			val = (temp >> 1) | (flag_c ? 0x80 : 0x00);
			flag_c = (temp & 1)==1;
			setnz.foo((byte)val);
			write.foo((byte)val);
		}
	} final ROR ror = new ROR();
	
	final private class RORA implements InstName { public void foo() {
			val = (((int)(regs.a&0xFF)) >> 1) | (flag_c ? 0x80 : 0x00);
			flag_c = (regs.a & 1)==1;
			regs.a = (byte)(val & 0xFF);
			setnz.foo(regs.a);
		}
	}final RORA rora = new RORA();
	
	final private class RTI implements InstName { public void foo() {
			regs.ps = pop.foo(); 
			cli.foo(); irq = true;
			af_to_ef.foo();
			regs.pc = (short)(pop.foo()&0x00FF);
			regs.pc |= (pop.foo() << 8);
		}
	} final RTI rti = new RTI();
	
	final private class RTS implements InstName { public void foo() {
		regs.pc = (short)(pop.foo() & 0x00FF);
		regs.pc |= (short)((pop.foo() << 8)&0x0000FF00); regs.pc += 1; }
	} final RTS rts = new RTS();
	
	final private class SBC implements InstName { 
		public void foo() {
			temp = (read.foo()) & 0x000000FF;
			if((regs.ps & AF_DECIMAL)!=0) {
				val = to_bin.foo(regs.a) - to_bin.foo((byte)temp) - (flag_c==true?0:1);
				val = val & 0x0000FFFF;
				flag_c = (val < 0x8000); // type of val is WORD.
				regs.a = to_bcd.foo((byte)val);
				setnz.foo(regs.a);
				cyc.foo(1);
			} else {
				temp = (read.foo()) & 0x000000FF;
				val = (regs.a&0xFF) - (temp&0xFF) - (flag_c==true?0:1);
				val = val & 0x0000FFFF;
				flag_c = (val < 0x00008000);
				flag_v = (((regs.a & 0x80)!=(temp & 0x80)) &&
							((regs.a & 0x80)!=(val&0x80)));
				regs.a = (byte)(val&0xFF);
				setnz.foo(regs.a);
			}
		}
	} final SBC sbc = new SBC();
	
	final private class SEC implements InstName { public void foo() {
		flag_c = true; }
	} final SEC sec = new SEC();
	
	final private class SED implements InstName { public void foo() {
		regs.ps |= AF_DECIMAL; }
	} final SED sed = new SED();
	
	final private class STA implements InstName { public void foo() { 
			write.foo(regs.a); 
		}}
	final STA sta = new STA();
	
	final private class STX implements InstName { public void foo() { write.foo(regs.x); }}
	final STX stx = new STX();
	
	final private class STY implements InstName { public void foo() { write.foo(regs.y); }}
	final STY sty = new STY();
	
	final private class SEI implements InstName { public void foo() 
		{ regs.ps |= (byte)AF_INTERRUPT; } }
	final SEI sei = new SEI();
	
	final private class TAX implements InstName {
		public void foo() { regs.x = regs.a;
			setnz.foo(regs.x);
		}
	} final TAX tax = new TAX();
	
	final private class TSX implements InstName {
		public void foo() { regs.x = (byte)(regs.sp & 0xFF);
			setnz.foo(regs.x);
		}
	} final TSX tsx = new TSX();
	
	final private class TXA implements InstName {
		public void foo() { regs.a = regs.x; setnz.foo(regs.a); }
	} final TXA txa = new TXA();
	
	final private class TXS implements InstName { 
		public void foo() { regs.sp = (short) ((short)0x0100 | ((short)regs.x&0xFF)); }}
	final TXS txs = new TXS();
	
	final private class TYA implements InstName {
		public void foo() { regs.a = regs.y; setnz.foo(regs.a); }
	} final TYA tya = new TYA();
	
	final private class TAY implements InstName {
		public void foo() { regs.y = regs.a; setnz.foo(regs.y); }
	} final TAY tay = new TAY();
	
	final class CLD implements InstName { 
		public void foo() { regs.ps &= (byte)~AF_DECIMAL; } }
	final CLD cld = new CLD();
	
	// ###################################
	// Interrupts!
	// ###################################
	final private class NMI {
		public void foo() {
			if(wai==true) { regs.pc++; wai = false; }
			push.foo((byte)(regs.pc >> 8));
			push.foo((byte)(regs.pc & 0xFF));
			sei.foo();
			ef_to_af.foo();
			push.foo(regs.ps);
			regs.pc = theFleurDeLisDriver.getWord(0xFFFA);
			nmi = true;
			cyc.foo(7);
		}
	} final NMI nmiFunc = new NMI();
	
	final private class IRQ {
		public void foo() {
			if(wai==true) { regs.pc++; wai = false; }
			if((regs.ps & AF_INTERRUPT)==0) {
				push.foo((byte)(regs.pc >> 8));
				push.foo((byte)(regs.pc & 0xFF));
				ef_to_af.foo();
				regs.ps &= ~AF_BREAK;
				push.foo(regs.ps);
				regs.pc = theFleurDeLisDriver.getWord(0xFFFE);
				cyc.foo(7);
				sei.foo();
			}
		}
	} final IRQ irqFunc = new IRQ();
	
	// Totally copied from BanXian's code.
	final public int oneInstruction() throws Exception {
		short temp, val;
		
		cycles = 0;
		if(total_inst_count == 123225) {
			System.out.println("a");
		}
		
		if(TommyHelper.IS_LOG_DISASSEMBLY == 1 && total_inst_count > TommyHelper.NUM_INSTS_SKIP) {
			prev_pc = regs.pc; 
			this_addr_mode = null;
			this_inst = null;
			logRegisters();
		} 
		
		af_to_ef.foo();
		
		pc = ((int)regs.pc) & 0x0000FFFF; // Always keep it unsigned
		regs.pc = (short)(pc+1); // So that we don't mess up with signed/unsigned
		byte opcode = theFleurDeLisDriver.getByte(pc);
		switch(opcode) {
		case (byte)0x00: // BRK
			am(null);exec(brk);cyc.foo(7);break;
		case (byte)0x01:
			am(indx); exec(ora); cyc.foo(6); break;
		case (byte)0x03: // INVALID1
			am(null); cyc.foo(1); break;
		case (byte)0x05: // ORA $12
			am(zpg); exec(ora); cyc.foo(3); break;
		case (byte)0x06: // Zpg ASL; ASL $56
			am(zpg); exec(asl); cyc.foo(5); break;
		case (byte)0x08: // PHP
			am(null); exec(php);cyc.foo(3); break;
		case (byte)0x09: // ORA #$12
			am(imm); exec(ora); cyc.foo(2); break;
		case (byte)0x0A:
			am(null);exec(asla);cyc.foo(2);break;
		case (byte)0x0D:
			am(abs); exec(ora); cyc.foo(4); break;
		case (byte)0x0E:
			am(abs); exec(asl); cyc.foo(6); break;
		case (byte)0x10: // 
			am(rel); exec(bpl); cyc.foo(2); break;
		case (byte)0x11:
			am(indy); exec(ora); cyc.foo(4); break;
		case (byte)0x15:
			am(zpgx); exec(ora); cyc.foo(4); break;
		case (byte)0x18:
			am(null);exec(clc);cyc.foo(2); break;
		case (byte)0x19:
			am(absy); exec(ora); cyc.foo(4); break;
		case (byte)0x1D: 
			am(absx); exec(ora); cyc.foo(4); break;
		case (byte)0x1E:
			am(absx); exec(asl); cyc.foo(6); break;
		case (byte)0x20: // JSR $1234
			am(abs); exec(jsr); cyc.foo(6); break;
		case (byte)0x21:
			am(indx);exec(and); cyc.foo(6); break;
		case (byte)0x24: // BIT $63
			am(zpg); exec(bit); cyc.foo(3); break;
		case (byte)0x25: // ZPG AND
			am(zpg); exec(and); cyc.foo(3); break;
		case (byte)0x26: // ROL $60
			am(zpg); exec(rol); cyc.foo(5); break;
		case (byte)0x28: // PLP
			am(null); exec(plp); cyc.foo(4); break;
		case (byte)0x29: // Imm AND
			am(imm); exec(and); cyc.foo(2); break;
		case (byte)0x2A:
			am(null);exec(rola);cyc.foo(2); break;
		case (byte)0x2C: // Abs BIT
			am(abs); exec(bit); cyc.foo(4); break;
		case (byte)0x2D:
			am(abs); exec(and); cyc.foo(4); break;
		case (byte)0x2E: // ROL
			am(abs); exec(rol); cyc.foo(6); break;
		case (byte)0x30: // BMI
			am(rel); exec(bmi); cyc.foo(2); break;
		case (byte)0x31:
			am(indy); exec(and); cyc.foo(5); break;
		case (byte)0x35:
			am(zpgx); exec(and); cyc.foo(4); break;
		case (byte)0x38:
			am(null);exec(sec); cyc.foo(2); break;
		case (byte)0x39:
			am(absy); exec(and); cyc.foo(4); break;
		case (byte)0x3D:
			am(absx); exec(and); cyc.foo(4); break;
		case (byte)0x3E:
			am(absx); exec(rol); cyc.foo(6); break;
		case (byte)0x40:
			am(null);exec(rti); cyc.foo(6); break;
		case (byte)0x45:
			am(zpg); exec(eor); cyc.foo(3); break;
		case (byte)0x46:
			am(zpg); exec(lsr); cyc.foo(5); break;
		case (byte)0x48:
			am(null);exec(pha); cyc.foo(3); break;
		case (byte)0x49:
			am(imm); exec(eor); cyc.foo(3); break;
		case (byte)0x4A:
			am(null); exec(lsra); cyc.foo(2); break;
		case (byte)0x4C: // Abs JMP; e.g. JMP $E77E
			am(abs); exec(jmp); cyc.foo(3); break;
		case (byte)0x4D:
			am(abs); exec(eor); cyc.foo(4); break;
		case (byte)0x4E:
			am(abs); exec(lsr); cyc.foo(6); break;
		case (byte)0x50:
			am(rel); bvc(); cyc.foo(1); break;
		case (byte)0x51:
			am(indy); exec(eor); cyc.foo(5); break;
		case (byte)0x55:
			am(zpgx); exec(eor); cyc.foo(4); break;
		case (byte)0x56:
			am(zpgx); exec(lsr); cyc.foo(6); break;
		case (byte)0x58:
			am(null);exec(cli); cyc.foo(2); break;
		case (byte)0x59:
			am(absy); exec(eor); cyc.foo(4); break;
		case (byte)0x60: // RTS
			am(null);exec(rts); cyc.foo(6); break;
		case (byte)0x61:
			am(indx); exec(adc); cyc.foo(6); break;
		case (byte)0x65: // ADC $5F
			am(zpg); exec(adc); cyc.foo(3); break;
		case (byte)0x66:
			am(zpg); exec(ror); cyc.foo(5); break;
		case (byte)0x68:
			am(null);exec(pla); cyc.foo(4); break;
		case (byte)0x69:
			am(imm); exec(adc); cyc.foo(2); break;
		case (byte)0x6A: // RORA
			am(null);exec(rora);cyc.foo(2); break;
		case (byte)0x6C:
			am(iabs);exec(jmp);cyc.foo(6); break;
		case (byte)0x6D:
			am(abs); exec(adc); cyc.foo(4); break;
		case (byte)0x6E:
			am(abs); exec(ror); cyc.foo(6); break;
		case (byte)0x70:
			am(rel); exec(bvs); cyc.foo(2); break;
		case (byte)0x71:
			am(indy);exec(adc); cyc.foo(5); break;
		case (byte)0x75:
			am(zpgx); exec(adc); cyc.foo(4); break;
		case (byte)0x76:
			am(zpgx); exec(ror); cyc.foo(6); break;
		case (byte)0x78: // SEI; sets interruption flag
			am(null);exec(sei); cyc.foo(2); break;
		case (byte)0x79:
			am(absy);exec(adc);cyc.foo(4); break;
		case (byte)0x7D:
			am(absx); exec(adc); cyc.foo(4); break;
		case (byte)0x7E:
			am(absx); exec(ror); cyc.foo(6); break;
		case (byte)0x81:
			am(indx); exec(sta); cyc.foo(6); break;
		case (byte)0x84: // ZPG STY
			am(zpg); exec(sty); cyc.foo(3); break;
		case (byte)0x85: // Zpg STA; e.g. STA $0A
			am(zpg); exec(sta); cyc.foo(3); break;
		case (byte)0x86: // ZPG STX
			am(zpg); exec(stx); cyc.foo(3); break;
		case (byte)0x88: // DEY
			am(null);exec(dey); cyc.foo(2); break;
		case (byte)0x8A: // TXA
			am(null);exec(txa);cyc.foo(2); break;
		case (byte)0x8C:
			am(abs); exec(sty); cyc.foo(4); break;
		case (byte)0x8D: // Abs STA; e.g. STA $0489
			am(abs); exec(sta); cyc.foo(4); break;
		case (byte)0x8E:
			am(abs); exec(stx); cyc.foo(4); break;
		case (byte)0x90: // BCC
			am(rel); exec(bcc); cyc.foo(2); break;
		case (byte)0x91: // INDY STA
			am(indy);exec(sta); cyc.foo(6); break;
		case (byte)0x94: // ZPGX STY
			am(zpgx); exec(sty); cyc.foo(4); break;
		case (byte)0x95:
			am(zpgx); exec(sta); cyc.foo(4); break;
		case (byte)0x98:
			am(null);exec(tya);cyc.foo(2); break;
		case (byte)0x99:
			am(absy);exec(sta); cyc.foo(5); break;
		case (byte)0x9A:
			am(null);exec(txs); cyc.foo(2); break;
		case (byte)0x9D:
			am(absx);exec(sta); cyc.foo(5); break;
		case (byte)0xA0: // LDY #$08
			am(imm); exec(ldy); cyc.foo(2); break;
		case (byte)0xA1: // INDX LDA
			am(indx); lda(); cyc.foo(6); break;
		case (byte)0xA2: // Imm LDX
			am(imm); exec(ldx); cyc.foo(2); break;
		case (byte)0xA4:
			am(zpg); exec(ldy); cyc.foo(3); break;
		case (byte)0xA5:
			am(zpg); lda(); ; cyc.foo(3); break;
		case (byte)0xA6:
			am(zpg); exec(ldx); cyc.foo(3); break;
		case (byte)0xA8:
			am(null); exec(tay); cyc.foo(2); break;
		case (byte)0xA9: // Imm LDA; e.g. LDA #$00
			am(imm); lda(); cyc.foo(2); break;
		case (byte)0xAA:
			am(null); exec(tax); cyc.foo(2); break;
		case (byte)0xAC: // Abs LDY
			am(abs); exec(ldy); cyc.foo(4); break;
		case (byte)0xAD: // Abs LDA; e.g. LDA $04BB
			am(abs); lda(); cyc.foo(4); break;
		case (byte)0xAE: // Abs LDX
			am(abs); exec(ldx); cyc.foo(4); break;
		case (byte)0xB0: // BCS
			am(rel); exec(bcs); cyc.foo(2); break;
		case (byte)0xB1: // INDY LDA
			am(indy); lda(); cyc.foo(5); break;
		case (byte)0xB4:
			am(zpgx); exec(ldy); cyc.foo(4); break;
		case (byte)0xB5:
			am(zpgx); lda(); cyc.foo(4); break;
		case (byte)0xB6:
			am(zpgy); exec(ldx); cyc.foo(4); break;
		case (byte)0xB8:
			am(null); clv(); cyc.foo(1); break;
		case (byte)0xB9: // LDA $1000, Y
			am(absy); lda(); cyc.foo(4); break;
		case (byte)0xBA: // TSX
			am(null); exec(tsx); cyc.foo(4); break;
		case (byte)0xBC:
			am(absx); exec(ldy); cyc.foo(4); break;
		case (byte)0xBD: // ABSX LDA
			am(absx); lda(); cyc.foo(4); break;
		case (byte)0xBE:
			am(absy); exec(ldx); cyc.foo(4); break;
		case (byte)0xC0: // CPY #$08
			am(imm); exec(cpy); cyc.foo(2); break;
		case (byte)0xC1:
			am(indx); exec(cmp); cyc.foo(6); break;
		case (byte)0xC4: // CPY $62
			am(zpg); exec(cpy); cyc.foo(3); break;
		case (byte)0xC5: // CMP $61
			am(zpg); exec(cmp); cyc.foo(3); break;
		case (byte)0xC6: // Zpg DEC; e.g. DEC $10
			am(zpg); exec(dec); cyc.foo(5); break;
		case (byte)0xC8: // INY
			am(null); exec(iny); cyc.foo(2); break;
		case (byte)0xC9: // IMM CMP
			am(imm); exec(cmp); cyc.foo(2); break;
		case (byte)0xCA: // DEX
			am(null);exec(dex); cyc.foo(2); break;
		case (byte)0xCC:
			am(abs); exec(cpy); cyc.foo(4); break;
		case (byte)0xCD:
			am(abs); exec(cmp); cyc.foo(4); break;
		case (byte)0xCE:
			am(abs); exec(dec); cyc.foo(6); break;
		case (byte)0xD0:
			am(rel); exec(bne); cyc.foo(2); break;
		case (byte)0xD1:
			am(indy); exec(cmp); cyc.foo(5); break;
		case (byte)0xD5:
			am(zpgx); exec(cmp); cyc.foo(4); break;
		case (byte)0xD6:
			am(zpgx); exec(dec); cyc.foo(6); break;
		case (byte)0xD8: // CLD
			am(null);exec(cld); cyc.foo(2); break;
		case (byte)0xD9: //
			am(absy); exec(cmp); cyc.foo(4); break;
		case (byte)0xDD:
			am(absx); exec(cmp); cyc.foo(4); break;
		case (byte)0xDE:
			am(absx); exec(dec); cyc.foo(6); break;
		case (byte)0xE0:
			am(imm); exec(cpx); cyc.foo(2); break;
		case (byte)0xE1:
			am(indx);exec(sbc); cyc.foo(6); break;
		case (byte)0xE4:
			am(zpg); exec(cpx); cyc.foo(3); break;
		case (byte)0xE5: // SBC $4E
			am(zpg); exec(sbc); cyc.foo(3); break;
		case (byte)0xE6:
			am(zpg); exec(inc); cyc.foo(5); break;
		case (byte)0xE8:
			am(null);exec(inx); cyc.foo(2); break;
		case (byte)0xE9:
			am(imm); exec(sbc); cyc.foo(2); break;
		case (byte)0xEA: // NOP
			am(null);exec(nop); cyc.foo(2); break;
		case (byte)0xEC:
			am(abs); exec(cpx); cyc.foo(4); break;
		case (byte)0xED:
			am(abs); exec(sbc); cyc.foo(4); break;
		case (byte)0xEE:
			am(abs); exec(inc); cyc.foo(5); break;
		case (byte)0xF0:
			am(rel); exec(beq); cyc.foo(2); break;
		case (byte)0xF1:
			am(indy); exec(sbc); cyc.foo(5); break;
		case (byte)0xF5:
			am(zpgx); exec(sbc); cyc.foo(4); break;
		case (byte)0xF6:
			am(zpgx); exec(inc); cyc.foo(6); break;
		case (byte)0xF9:
			am(absy);exec(sbc); cyc.foo(4); break;
		case (byte)0xFD:
			am(absx); exec(sbc); cyc.foo(4); break;
		case (byte)0xFE:
			am(absx); exec(inc); cyc.foo(6); break;
		default:
			System.err.println("Op code " + 
					String.format("%02X @ %04X (Instr #%d)", opcode, (regs.pc&0xFFFF),
							total_inst_count)
					+ " not implemented!"
					+ String.format(" (See Inst dump line #%d)",
							total_inst_count - TommyHelper.NUM_INSTS_SKIP));
			throw new Exception();
		}
		
		if(stp == false) {
			if(nmi == false) {
				nmiFunc.foo();
			}
			if(irq == false) { // TODO IRQ
				irqFunc.foo();
			}
		}
		
		ef_to_af.foo();
		total_inst_count++;

		// 1.5. Input test
		if(TommyHelper.UP_POKE_NUMINST != -1) {
			if(total_inst_count == TommyHelper.UP_POKE_NUMINST) {
				theFleurDeLisDriver.keypadmatrix[6][2]=1;
			}
		}
		
		if(TommyHelper.IS_LOG_DISASSEMBLY == 1 && total_inst_count > TommyHelper.NUM_INSTS_SKIP) {
			// logRegisters(); // Banxian's dump log prints the registers *BEFORE* the curr inst
			logInst(); // logOperand() is called from am()
			//if(total_inst_count > 100000) 
			String my_line = String.format("%d %04X  %-8s %s %-8s %s",
				total_inst_count,
			prev_pc, machcode_log, inst_log, operand_log, regs_log);
			if(total_inst_count > TommyHelper.NUM_INSTS_SKIP+1) {
				TommyHelper.dumpAndCompare(my_line);
				TommyHelper.my_line = my_line;
			}
		}
		
		return cycles;
	}
}
