package kr.ac.mju.islab;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import it.unisa.dia.gas.jpbc.Element;
import it.unisa.dia.gas.jpbc.Field;
import kr.ac.mju.islab.RewardProto.RewardPacket;

/**
 * RewardAndroidServer class is a port of RewardServer for Android.
 * Its usage is same as RewardServer. Check jDoc for RewardServer for more details.
 * 
 * @author jwlee
 * @version 1.0.0
 * @since 2017-03-08
 */
public class RewardAndroidServer implements Runnable {
	public int bindPort;
	private RewardScheme rewardScheme;
	protected RewardPacket recvPacket;

	/**
	 * Inherit and concrete these methods to respond directly to any protocol
	 */
    public boolean onIssueRequest() {
    	return true;
    }
    public boolean onVerifyRequest() {
    	return true;
    }
    public boolean onAggVerifyRequest() {
    	return true;
    }
    public boolean onYRequest() {
    	return true;
    }
	
	/**
	 * Class constructor specifying bind address, bind port,
	 * and a object of RewardScheme which is used as an engine of RewardAndroidServer. 
	 * 
	 * @param bindPort the bind port of the server
	 * @param rewardScheme the object of RewardScheme. You can define custom parameters on it
	 */
    public RewardAndroidServer(int bindPort, RewardScheme rewardScheme) {
    	this.bindPort = bindPort;
    	this.rewardScheme = rewardScheme;
    }

    /**
     * RewardAndroidServer implements Runnable, so could act as thread.
     * <p>
     * Usage: <br>
     * Thread rewardAndroidServer = new Thread(new RewardAndroidServer(3575, new RewardScheme())); <br>
     * rewardAndroidServer.start();
     */
    @Override
    public void run() {
        InetSocketAddress sockAddr = new InetSocketAddress(bindPort);
        
		ServerSocketChannel serverSock = null;
		try {
			serverSock = ServerSocketChannel.open();
			serverSock.socket().bind(sockAddr);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
			
		while(true){
			SocketChannel sockChannel = null;
			try {
				sockChannel = serverSock.accept();
			} catch (IOException e1) {
				e1.printStackTrace();
			}

			// Read
			ByteBuffer buf = ByteBuffer.allocate(32768);	// Capable of dealing with #100 aggregated receipt
			try {
				sockChannel.read(buf);
			} catch (Exception e1) {
				if (sockChannel == null) {
					Thread.currentThread().interrupt();
					return;
				}
				e1.printStackTrace();
			}

			// Process header to get a length of received packet.
			byte[] packetLenInfo = new byte[4];
			buf.flip();
			try{
				buf.get(packetLenInfo);
			} catch (Exception e) { }	// Ignore errors on client disconnection.
			buf.compact();
			
			int packetLen = ByteBuffer.wrap(packetLenInfo).getInt();
			if (packetLen == 0) {
				continue;
			}
			byte[] recvBytePacket = new byte[packetLen];
			if (recvBytePacket.length > 32768) {
				System.out.println("Buffer size is smaller than received packet size: " + recvBytePacket.length);
			}
			buf.flip();
			buf.get(recvBytePacket);
			buf.compact();
			// END

			recvPacket = null;
			RewardPacket sendPacket = null;
			try {
				recvPacket = RewardPacket.parseFrom(recvBytePacket);
			} catch (InvalidProtocolBufferException e) {
				e.printStackTrace();
			}

			// Process packet according to its pid.
			if (recvPacket.getPid() == 1) { // RewardScheme.recIssueMaster
				if (this.onIssueRequest() == false) {	// Notifying that the protocol is refused.
					sendPacket = RewardPacket.newBuilder()
							.setPid(999)
							.build();
				}
				else {
					Element h = rewardScheme.G1.newElementFromBytes(recvPacket.getE1().toByteArray()).getImmutable();
					Element psi = rewardScheme.recIssueMaster(h).getImmutable();

					sendPacket = RewardPacket.newBuilder()
							.setPid(1)	// Is not necessary. Append for analysis
							.setE1(ByteString.copyFrom(psi.toBytes()))
							.build();
				}
			}
			else if (recvPacket.getPid() == 2) {	// RewardScheme.verify
				if (this.onVerifyRequest() == false) {	// Notifying that the protocol is refused.
					sendPacket = RewardPacket.newBuilder()
							.setPid(999)
							.build();
				}
				else {
					Element sigma = rewardScheme.G1.newElementFromBytes(recvPacket.getE1().toByteArray()).getImmutable();
					Element s = rewardScheme.Zr.newElementFromBytes(recvPacket.getE2().toByteArray()).getImmutable();
					Element y = rewardScheme.G2.newElementFromBytes(recvPacket.getE3().toByteArray()).getImmutable();
					
					sendPacket = RewardPacket.newBuilder()
							.setPid(2)	// Is not necessary. Append for analysis
							.setIsValid(rewardScheme.verify(sigma, s, y))
							.build();
				}
			}
			else if (recvPacket.getPid() == 3) {	// RewardScheme.aggVerify
				if (this.onAggVerifyRequest() == false) {	// Notifying that the protocol is refused.
					sendPacket = RewardPacket.newBuilder()
							.setPid(999)
							.build();
				}
				else {
					Element sigmaAgg = rewardScheme.G1.newElementFromBytes(recvPacket.getE1().toByteArray()).getImmutable();
					List<Element> sList = ByteStringListToElementList(recvPacket.getEList1List(), rewardScheme.Zr);
					List<Element> yList = ByteStringListToElementList(recvPacket.getEList2List(), rewardScheme.G2);
					
					sendPacket = RewardPacket.newBuilder()
							.setPid(3)	// Is not necessary. Append for analysis
							.setIsValid(rewardScheme.aggVerify(sigmaAgg, sList, yList))
							.build();
				}
			}
			else if (recvPacket.getPid() == 101) {	// RewardScheme.y
					sendPacket = RewardPacket.newBuilder()
							.setPid(101)	// Is not necessary. Append for analysis
							.setE1(ByteString.copyFrom(rewardScheme.y.toBytes()))
							.build();
			}
			else {
				System.err.println("Unexpected: " + recvPacket.getAllFields());
				return;
			}

			// Write
			// Prepend packetLenInfo to the RewardPacket, so that the other side can determine the packet size
			int sendPacketLen = sendPacket.toByteArray().length;
			byte[] sendPacketLenInfo = ByteBuffer.allocate(4).putInt(sendPacketLen).array();

			ByteBuffer sendBuf = ByteBuffer.allocate(4 + sendPacketLen);
			sendBuf.put(sendPacketLenInfo);
			sendBuf.put(sendPacket.toByteArray());
			sendBuf.flip();
			// END
			
			while (sendBuf.hasRemaining()) {
				try {
					sockChannel.write(sendBuf);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			
			try {
				sockChannel.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
    }

     @SuppressWarnings("rawtypes")
	 private List<Element> ByteStringListToElementList(List<ByteString> bList, Field g) {
    	 List<Element> eList= new ArrayList<Element>();
    	 for (ByteString b : bList) {
    		 eList.add(b == null ? null : g.newElementFromBytes(b.toByteArray()).getImmutable());
    	 }
    	 return eList;
     }
     
     public static void main(String[] args) {
        try {
			Thread rewardServer = new Thread(new RewardAndroidServer(3575, new RewardScheme()));
			rewardServer.start();
			System.out.println("Server started");
        } catch (Exception ex) {
            Logger.getLogger(RewardAndroidServer.class.getName()).log(Level.SEVERE, null, ex);
        }
     }
}
