package kr.ac.mju.islab;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import it.unisa.dia.gas.jpbc.Element;
import it.unisa.dia.gas.jpbc.Field;
import kr.ac.mju.islab.RewardProto.RewardPacket;

/**
 * RewardServer class is a server-side concrete implementation of 
 * 'Privacy-Aware Reward System'.
 * <p>
 * RewardServer responds to query from a client-side concrete implementation,
 * which are RewardQuery objects.
 * <p>
 * If you are only interested on application,
 * you do not need to check detailed class like RewardScheme.
 * 
 * @author jwlee
 * @version 1.0.0
 * @since 2017-03-08
 */
public class RewardServer implements Runnable {
	public int bindPort;
	private RewardScheme rewardScheme;
    private AsynchronousServerSocketChannel serverSock = null;
    private AsynchronousChannelGroup group = null;

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
	 * and a object of RewardScheme which is used as an engine of RewardServer. 
	 * 
	 * @param bindPort the bind port of the server
	 * @param rewardScheme the object of RewardScheme. You can define custom parameters on it
	 */
    public RewardServer(int bindPort, RewardScheme rewardScheme) {
    	this.bindPort = bindPort;
    	this.rewardScheme = rewardScheme;
    }

    /**
     * RewardServer implements Runnable, so could act as thread.
     * <p>
     * Usage: <br>
     * Thread rewardServer = new Thread(new RewardServer(3575, new RewardScheme())); <br>
     * rewardServer.start();
     */
    @Override
    public void run() {
        InetSocketAddress sockAddr = new InetSocketAddress(bindPort);
        
        //create a socket channel and bind to local bind address
		try {
			group = AsynchronousChannelGroup.withThreadPool(Executors.newCachedThreadPool());
			serverSock = AsynchronousServerSocketChannel.open(group).bind(sockAddr);
		} catch (IOException e) {
			e.printStackTrace();
		}
        
        //start to accept the connection from client
        serverSock.accept(serverSock, new CompletionHandler<AsynchronousSocketChannel,AsynchronousServerSocketChannel>() {

            @Override
            public void completed(AsynchronousSocketChannel sockChannel, AsynchronousServerSocketChannel serverSock) {
				//a connection is accepted, start to accept next connection
				serverSock.accept(serverSock, this);

				//start to read message from the client
				startRead(sockChannel);
            }

            @Override
            public void failed(Throwable exc, AsynchronousServerSocketChannel serverSock) {
            	if (serverSock.isOpen() == true) {
					System.err.println("Fail to accept a connection");
            	}
            }
        } );

        // wait until group.shutdown()/shutdownNow(), or the thread is interrupted:
        try {
			group.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			System.out.println("Server interrupted.");
			Thread.currentThread().interrupt();
			try {
				serverSock.close();
				serverSock = AsynchronousServerSocketChannel.open().bind(sockAddr);
			} catch (IOException e1) { }
		} finally {
			group.shutdown();
		}
    }
    
    /**
     * stratRead, startWrite implements underlying protocols for server-client
     * communication.
     * <p>
     * Unless you have to modify protocols, you don't have to look inside those
     * methods
     * 
     * @param sockChannel socket channel
     */
    private void startRead(AsynchronousSocketChannel sockChannel) {
        final ByteBuffer buf = ByteBuffer.allocate(32768);	// Capable of dealing with #100 aggregated receipt
        
        //read message from client
        sockChannel.read(buf, sockChannel, new CompletionHandler<Integer, AsynchronousSocketChannel>() {
            @Override
            public void completed(Integer result, AsynchronousSocketChannel channel) {
            	// Process header to get a length of received packet.
            	byte[] packetLenInfo = new byte[4];
				buf.flip();
				try{
					buf.get(packetLenInfo);
				} catch (Exception e) { }	// Ignore errors on client disconnection.
				buf.compact();
            	
				int packetLen = ByteBuffer.wrap(packetLenInfo).getInt();
				System.out.println("Received Packet Length: " + packetLen);
				if (packetLen == 0) {
					return;
				}
				byte[] recvBytePacket = new byte[packetLen];
				if (recvBytePacket.length > 32768) {
					System.out.println("Buffer size is smaller than received packet size: " + recvBytePacket.length);
				}
				buf.flip();
				buf.get(recvBytePacket);
				buf.compact();
				// END

                RewardPacket recvPacket = null;
                RewardPacket sendPacket = null;
				try {
					recvPacket = RewardPacket.parseFrom(recvBytePacket);
				} catch (InvalidProtocolBufferException e) {
					e.printStackTrace();
				}

				// Process packet according to its pid.
				if (recvPacket.getPid() == 1) { // RewardScheme.recIssueMaster
					if (onIssueRequest() == false) {	// Notifying that the protocol is refused.
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
					if (onVerifyRequest() == false) {	// Notifying that the protocol is refused.
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
					if (onAggVerifyRequest() == false) {	// Notifying that the protocol is refused.
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
					if (onYRequest() == false) {	// Notifying that the protocol is refused.
						sendPacket = RewardPacket.newBuilder()
								.setPid(999)
								.build();
					}
					else {
						sendPacket = RewardPacket.newBuilder()
								.setPid(101)	// Is not necessary. Append for analysis
								.setE1(ByteString.copyFrom(rewardScheme.y.toBytes()))
								.build();
					}
				}
				else {
					System.err.println("Unexpected: " + recvPacket.getAllFields());
					startRead(channel);
					return;
				}
				System.out.println("Recv Packet ID: " + recvPacket.getPid());
				System.out.println("Packet Contents: " + recvPacket.getAllFields());

				// Prepend packetLenInfo to the RewardPacket, so that the other side can determine the packet size
				int sendPacketLen = sendPacket.toByteArray().length;
				byte[] sendPacketLenInfo = ByteBuffer.allocate(4).putInt(sendPacketLen).array();

				ByteBuffer sendBuf = ByteBuffer.allocate(4 + sendPacketLen);
				sendBuf.put(sendPacketLenInfo);
				sendBuf.put(sendPacket.toByteArray());
				sendBuf.flip();
                //END

                startWrite(channel, sendBuf);
            }

            @Override
            public void failed(Throwable exc, AsynchronousSocketChannel channel ) {
            	System.err.println("Failed to receive packet from client");
            }
        });
    }
    
     private void startWrite(AsynchronousSocketChannel sockChannel, final ByteBuffer buf) {
         sockChannel.write(buf, sockChannel, new CompletionHandler<Integer, AsynchronousSocketChannel>() {

             @Override
             public void completed(Integer result, AsynchronousSocketChannel channel) { 
            	try {
					channel.close();
					Thread.currentThread().interrupt();
				} catch (IOException e) {
				}
             }

             @Override
             public void failed(Throwable exc, AsynchronousSocketChannel channel) {
                 System.err.println( "Fail to send packet to client");
             }
         });
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
			Thread rewardServer = new Thread(new RewardServer(3575, new RewardScheme()));
			rewardServer.start();
			System.out.println("Server started");
        } catch (Exception ex) {
            Logger.getLogger(RewardServer.class.getName()).log(Level.SEVERE, null, ex);
        }
     }
}
