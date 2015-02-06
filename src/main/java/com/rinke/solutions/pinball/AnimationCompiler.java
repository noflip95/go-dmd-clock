package com.rinke.solutions.pinball;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.LittleEndianDataInputStream;
import com.rinke.solutions.pinball.renderer.FrameSet;

/**
 * class that compiles an animation into a binary file 
 * @author sr
 */
public class AnimationCompiler {

	private static Logger LOG = LoggerFactory.getLogger(AnimationCompiler.class); 
	
	public static List<Animation> readFromRunDMDFile(String filename) throws IOException {
		List<Animation> anis = new ArrayList<Animation>();
		CompiledAnimation a = new CompiledAnimation(AnimationType.PNG_SEQ,
				"foo", 0, 0, 1, 1, 0);
		a.setRefreshDelay(100);
		//LittleEndianDataInputStream is = new LittleEndianDataInputStream(new FileInputStream(filename));
		RandomAccessFile is = new RandomAccessFile(filename, "r");
		//is.skip(0x01000);
//		int lolcount = 0;
//		byte[] lol = new byte[4];
//		long offset = 0x1000;
//		while( is.read(lol) == 4 ) {
//			//lol!
//			if( lol[0] != lol[2] ) throw new RuntimeException("bad lol");
//			int len = is.readInt();
//			System.out.println("offset: "+Long.toHexString(offset)+" lol"+lolcount+": "+len);
//			lolcount++;
//			is.skip(len-8);
//			offset += len;
//		}
		for(int i = 0; i<1500; i++) {
			byte[] f1 = new byte[512];
			is.seek(0x1000+i);
			is.readFully(f1);
			// revers transform
			DMD dmd = new DMD(128, 32);
			byte[] f = transform(f1,dmd);
			a.addFrames(f, f);
		}
		anis.add(a);
		is.close();
		return anis;
	}
	
	static byte[] m2 = { 
			(byte) 0b10000000,
			(byte) 0b01000000,
			(byte) 0b00100000,
			(byte) 0b00010000,
			(byte) 0b00001000,
			(byte) 0b00000100,
			(byte) 0b00000010,
			(byte) 0b00000001,
			};

	
	private static byte[] transform(byte[] f1, DMD dmd) {
		byte[] r = new byte[dmd.getFrameSizeInByte()];
		for(int y=0; y<dmd.getHeight();y++) {
			for(int x = 0; x <dmd.getWidth();x++) {
				if( dmd.getPixel(f1, x, y)) {
					r[y*dmd.getBytesPerRow() + x/8] |= m2[x % 8];
				}
			}
		}
		return r;
	}

	public static List<Animation> readFromCompiledFile(String filename) {
		List<Animation> anis = new ArrayList<>();
		LOG.info("reading animations from {}",filename);
		DataInputStream is = null;
		try {
			is = new DataInputStream(new FileInputStream(filename));
			byte[] magic = new byte[4];
			is.read(magic); //
			is.readShort(); // version
			int count = is.readShort();
			LOG.info("reading {} animations from {}",count, filename);
			while(count>0) {
				String name = is.readUTF();
				int cycles = is.readShort();
				int holdCycles = is.readShort();
				int clockFrom = is.readShort();
				boolean clockSmall = is.readBoolean();
				boolean front = is.readBoolean();
				int clockXOffset = is.readShort();
				int clockYOffset = is.readShort();
				int refreshDelay = is.readShort();
				AnimationType type = AnimationType.values()[is.readByte()];
				// create complied animations
				CompiledAnimation a = new CompiledAnimation(type, "foo", 0, 0, 1, cycles, holdCycles);
				a.setRefreshDelay(refreshDelay);
				a.setClockFrom(clockFrom);
				a.setClockSmall(clockSmall);
				a.setClockXOffset(clockXOffset);
				a.setClockYOffset(clockYOffset);
				a.setClockInFront(front);
				int frameSets = is.readShort();
				while(frameSets>0) {
					int size = is.readShort();
					byte[] f1 = new byte[size];
					byte[] f2 = new byte[size];
					is.readFully(f1 );
					is.readFully(f2);
					a.addFrames(transform(f1,new DMD(128, 32)), transform(f2,new DMD(128, 32)));
					frameSets--;
				}
				count--;
				anis.add(a);
			}
		} catch (IOException e) {
			LOG.error("problems when reading file {}", filename,e);
		} finally {
			if( is != null ) {
				try {
					is.close();
				} catch (IOException e) {
					LOG.error("problems when closing file {}", filename);
				}
			}
		}
		LOG.info("successful read {} anis", anis.size());
		return anis;
	}
	
	public static void main(String[] args)  {
		List<Animation> anis = AnimationFactory.buildAnimations("animations.properties");
		String filename = "foo.ani";
		compile(anis, filename);
	}

	public static void compile(List<Animation> anis, String filename) {
		DataOutputStream os = null;
		try {
			LOG.info("writing animations to {}",filename);
			os = new DataOutputStream(new FileOutputStream(filename));
			os.writeBytes("ANIM"); //
			os.writeShort(1); // version
			os.writeShort(anis.size());
			LOG.info("writing {} animations", anis.size());
			for (Animation a : anis) {
				// write meta data
				os.writeUTF(a.getName());
				os.writeShort(a.getCycles());
				os.writeShort(a.getHoldCycles());
				// clock while animating
				os.writeShort(a.getClockFrom()-a.getStart());
				os.writeBoolean(a.isClockSmall());
				os.writeBoolean(a.isClockInFront());
				os.writeShort(a.getClockXOffset());
				os.writeShort(a.getClockYOffset());
				
				os.writeShort(a.getRefreshDelay());
				os.writeByte(a.getType().ordinal());
				int count = a.getFrameSetCount();
				os.writeShort(count);
				// write frames
				LOG.info("writing {} frames", count);
				for(int i = 0; i<count;i++) {
					DMD dmd = new DMD(128, 32);
					os.writeShort(dmd.getFrameSizeInByte());
					FrameSet frameSet = a.render(dmd,false);
					// transform in target format
					os.write(dmd.transformFrame(frameSet.frame1));
					os.write(dmd.transformFrame(frameSet.frame2));
				}
			}
			LOG.info("done");
		} catch (IOException e) {
			LOG.error("problems when wrinting file {}", filename);
		} finally {
			if( os != null ) {
				try {
					os.close();
				} catch (IOException e) {
					LOG.error("problems when closing file {}", filename);
				}
			}
		}
	}
	
}
