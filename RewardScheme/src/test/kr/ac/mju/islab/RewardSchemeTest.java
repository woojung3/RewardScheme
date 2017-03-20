package test.kr.ac.mju.islab;

import kr.ac.mju.islab.RewardProto.RewardPacket;
import kr.ac.mju.islab.RewardQuery;
import kr.ac.mju.islab.RewardScheme;
import kr.ac.mju.islab.RewardServer;
import kr.ac.mju.islab.RewardClient;
import kr.ac.mju.islab.secParam.CurveName;
import kr.ac.mju.islab.secParam.HashName;

import it.unisa.dia.gas.jpbc.*;
import it.unisa.dia.gas.plaf.jpbc.pairing.PairingFactory;

import static org.junit.Assert.*;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.ArrayList;

import org.junit.Ignore;
import org.junit.Test;


/*
 * This is a jUnit unit test class for RewardScheme.
 * If you are simply using RewardScheme, you don't need to check through this class.
 */
public class RewardSchemeTest {
	
	/*
	 * RewardQuery related tests.
	 */
	@Test
	public void queryCheck() throws IOException, InterruptedException {
		// Setup
		Thread rewardServer = new Thread(new RewardServer("127.0.0.1", 5575, new RewardScheme()));
		rewardServer.start();
		RewardQuery query = new RewardQuery("127.0.0.1", 5575);
		
		query.configureAsHelper();
		byte[][] rtn = query.recIssueHelperPre();
		byte[] s = rtn[0];
		byte[] r = rtn[1];
		byte[] h = rtn[2];
		byte[] psi = query.recIssueMaster(h);
		byte[] sigma = query.recIssueHelperPost(r, psi, query.gety());
			
		assertEquals(true, query.verify(sigma, s, query.gety()));
		
		rewardServer.interrupt();
		rewardServer.join();
	}

	@Test
	public void queryCheck10() throws IOException, InterruptedException {
		// Setup
		Thread rewardServer = new Thread(new RewardServer("127.0.0.1", 4575, new RewardScheme()));
		rewardServer.start();
		RewardQuery query = new RewardQuery("127.0.0.1", 4575);

		for (int i=0; i<10; i++) {
			query.configureAsHelper();
			byte[][] rtn = query.recIssueHelperPre();
			byte[] s = rtn[0];
			byte[] r = rtn[1];
			byte[] h = rtn[2];
			byte[] psi = query.recIssueMaster(h);
			byte[] sigma = query.recIssueHelperPost(r, psi, query.gety());
				
			assertEquals(true, query.verify(sigma, s, query.gety()));
		}

		rewardServer.interrupt();
		rewardServer.join();
	}

	@Ignore
	@Test
	public void queryCheck500() throws IOException, InterruptedException {
		// Setup
		Thread rewardServer = new Thread(new RewardServer("127.0.0.1", 3575, new RewardScheme()));
		rewardServer.start();
		RewardQuery query = new RewardQuery("127.0.0.1", 3575);

		for (int i=0; i<500; i++) {
			query.configureAsHelper();
			byte[][] rtn = query.recIssueHelperPre();
			byte[] s = rtn[0];
			byte[] r = rtn[1];
			byte[] h = rtn[2];
			byte[] psi = query.recIssueMaster(h);
			byte[] sigma = query.recIssueHelperPost(r, psi, query.gety());
				
			assertEquals(true, query.verify(sigma, s, query.gety()));
		}

		rewardServer.interrupt();
		rewardServer.join();
	}

	@Test
	public void aggQueryCheck10() throws IOException, InterruptedException {
		// Setup
		Thread rewardServer = new Thread(new RewardServer("127.0.0.1", 2575, new RewardScheme()));
		rewardServer.start();
		RewardQuery query = new RewardQuery("127.0.0.1", 2575);
		List<byte[]> sigmaList = new ArrayList<byte[]>();
		List<byte[]> sList = new ArrayList<byte[]>();
		List<byte[]> yList = new ArrayList<byte[]>();

		for (int i=0; i<10; i++) {
			query.configureAsHelper();
			byte[][] rtn = query.recIssueHelperPre();
			byte[] s = rtn[0];
			byte[] r = rtn[1];
			byte[] h = rtn[2];
			byte[] psi = query.recIssueMaster(h);
			byte[] sigma = query.recIssueHelperPost(r, psi, query.gety());
			sigmaList.add(sigma);
			sList.add(s);
			yList.add(query.gety());
		}
		byte[] sigmaAgg = query.aggregate(sigmaList);
		assertEquals(true, query.aggVerify(sigmaAgg, sList, yList));

		rewardServer.interrupt();
		rewardServer.join();
	}

	/*
	 * RewardClient related tests.
	 */
	@Test
	public void packetCheck() throws IOException, InterruptedException {
		Thread rewardServer = new Thread(new RewardServer("127.0.0.1", 1575, new RewardScheme()));
		rewardServer.start();
		new RewardClient("127.0.0.1", 1575, RewardPacket.newBuilder()
				.setPid(101)
				.build());

		rewardServer.interrupt();
		rewardServer.join();
	}
	
	/*
	 * Belows are RewardScheme related tests. 
	 */
	@Test
	public void issuVeriCheck() {
		// Setup
		RewardScheme rewardS = new RewardScheme();

		// Play Helper's role in recIssue Process.
		Element[] rtn = rewardS.recIssueHelperPre();
		Element s = rtn[0];
		Element r = rtn[1];
		Element h = rtn[2];

		// Pass h to master
		Element psi = rewardS.recIssueMaster(h);

		// Generate sigma from master's response, psi.
		Element sigma = rewardS.recIssueHelperPost(r, psi, rewardS.y);
		
		// UnitTest Part
		assertEquals(rewardS.verify(sigma, s, rewardS.y), true);
	}
	
	@Test
	public void issuVeriCheck10() {
		// Setup
		RewardScheme rewardS = new RewardScheme();
		
		// Check
		for (int i=0; i<10; i++) {
			Element[] rtn = rewardS.recIssueHelperPre();
			Element s = rtn[0];
			Element r = rtn[1];
			Element h = rtn[2];
			Element psi = rewardS.recIssueMaster(h);
			Element sigma = rewardS.recIssueHelperPost(r, psi, rewardS.y);

			assertEquals(rewardS.verify(sigma, s, rewardS.y), true);
		}
	}

	@Test
	public void aggVeriCheck() {
		// Setup
		RewardScheme rewardS = new RewardScheme();
		List<Element> sigmaList = new ArrayList<Element>();
		List<Element> sList = new ArrayList<Element>();
		List<Element> yList = new ArrayList<Element>();
		
		// Aggregate
		for (int i=0; i<10; i++) {
			Element[] rtn = rewardS.recIssueHelperPre();
			Element s = rtn[0];
			Element r = rtn[1];
			Element h = rtn[2];
			Element psi = rewardS.recIssueMaster(h);
			Element sigma = rewardS.recIssueHelperPost(r, psi, rewardS.y);
			sigmaList.add(sigma);
			sList.add(s);
			yList.add(rewardS.y);
		}
		Element sigmaAgg = rewardS.aggregate(sigmaList);
		
		// Verify
		assertEquals(true, rewardS.aggVerify(sigmaAgg, sList, yList));
	}
	
	@Test
	public void rewardHashCheck() {
		// Setup
		RewardScheme rewardS = new RewardScheme();
		assertEquals(rewardS.hash.getHexDigest("abc"), "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");

		rewardS = new RewardScheme(CurveName.a, HashName.SHA512);
		assertEquals(rewardS.hash.getHexDigest("abc"), "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f");
		
		/*
		 * To set an element from a hash:
		 * byte[] hash = ... // Generate an hash from m (48-bit hash)
		 * Element h = pairing.getG1().newElement().setFromHash(hash, 0, hash.length);
		 */
	}

	/*
	 * Protocol Buffers related tests.
	 */
	@Test
	public void protobufWorkingCheck() throws InvalidProtocolBufferException {
		// Setup
		RewardScheme rewardS = new RewardScheme();
		
		RewardPacket rPacket = RewardPacket.newBuilder()
				.setPid(1)
				.setE1(ByteString.copyFrom(rewardS.y.toBytes()))
				.build();
		byte[] rPacketSerialized = rPacket.toByteArray();
		RewardPacket rPacketDeserialized;

		rPacketDeserialized = RewardPacket.parseFrom(rPacketSerialized);
		assertEquals(rewardS.y, rewardS.G1.newElementFromBytes(rPacketDeserialized.getE1().toByteArray()));
	}
	
	@Test
	public void intToBytesCheck() {
		int i = 1695609641;
		byte[] bytes = ByteBuffer.allocate(4).putInt(i).array();
		int rtnInt = ByteBuffer.wrap(bytes).getInt();
		assertEquals(i, rtnInt);
	}

	/*
	 * Belows are jPBC related tests. If you are new to (j)PBC, you can use these as a 
	 * simple self-explained manual.
	 * (j)PBC/Arcanum url: 
	 * http://adecaro.github.io/arcanum/docs/linearmaps/linearmaps.html
	 */
	@Test
	public void initPairing() {
		/*
		 * Assume that the pairing parameters (for bilinear or multilinear maps) 
		 * are stored in a file called params.properties. 
		 * Then, the following code instantiate the appropriate class 
		 * implementing the Pairing interface.
		 */
		@SuppressWarnings("unused")
		Pairing pairing = PairingFactory.getPairing("params/curves/d224.properties");
		
		/*
		 * For bilinear maps only, to use the PBC wrapper and gain in performance, 
		 * the usePBCWhenPossible property of the pairing factory must be set
		 * (If PBC and the JPBC wrapper are not installed properly then the factory 
		 * will resort to the JPBC pairing implementation).
		 */
		PairingFactory.getInstance().setUsePBCWhenPossible(true);
	}

	@Test
	@SuppressWarnings("rawtypes")
	public void algebraicStruct() {
		// Setup
		Pairing pairing = PairingFactory.getPairing("params/curves/d224.properties");
		PairingFactory.getInstance().setUsePBCWhenPossible(true);

		// Get Fields
		Field Zr = pairing.getZr();
		Field G1 = pairing.getG1();
		Field G2 = pairing.getG2();
		Field GT = pairing.getGT();
		
		// is symmetric?
		assertEquals(pairing.isSymmetric(), false);

		// generated Fields' correctness check
		assertEquals(Zr.getOrder(), new BigInteger("15028799613985034465755506450771561352583254744125520639296541195021"));
		assertEquals(G1.getOrder(), new BigInteger("15028799613985034465755506450771561352583254744125520639296541195021"));
		assertEquals(G2.getOrder(), new BigInteger("15028799613985034465755506450771561352583254744125520639296541195021"));
		assertEquals(GT.getOrder(), new BigInteger("15028799613985034465755506450771561352583254744125520639296541195021"));
	}
	
	@Test
	@SuppressWarnings("rawtypes")
	public void elementGenFromField() {
		// Setup
		Pairing pairing = PairingFactory.getPairing("params/curves/d224.properties");
		PairingFactory.getInstance().setUsePBCWhenPossible(true);
		Field Zr = pairing.getZr();
		
		// uninitialized element
		assertNotEquals(Zr.newElement(), 0);
		
		// zero element
		assertEquals(Zr.newZeroElement().isZero(), true);
		
		// one element
		assertEquals(Zr.newOneElement().isOne(), true);
		
		// random element
		assertNotEquals(Zr.newRandomElement(), Zr.newElement());
		
		// element with a specified value
		assertEquals(Zr.newElement(5).toString(), "5");
		assertEquals(Zr.newElement(new BigInteger("123")).toString(), "123");
	}

	@Test
	@SuppressWarnings("rawtypes")
	public void elementGen() {
		// Setup
		Pairing pairing = PairingFactory.getPairing("params/curves/d224.properties");
		PairingFactory.getInstance().setUsePBCWhenPossible(true);
		Field G1 = pairing.getG1();
		Element e = G1.newRandomElement();
		
		// assigning elements
		assertEquals(e.setToZero().isZero(), true);
		assertEquals(e.setToOne().isOne(), true);
		assertNotEquals(e.setToRandom(), G1.newElement());
	}

	@Test
	@SuppressWarnings("rawtypes")
	public void doPairing() {
		// Setup
		Pairing pairing = PairingFactory.getPairing("params/curves/d224.properties");
		PairingFactory.getInstance().setUsePBCWhenPossible(true);
		Field G1= pairing.getG1();
		Field G2= pairing.getG2();
		
		/*
		 * DO NOT FORGET TO GET_IMMUTABLE/DUPLICATE THE DATA! ALL METHODS CHANGES 
		 * THE VARIABLE ITSELF!
		 */
		// Bilinearity check
		Element a = G1.newRandomElement().getImmutable();
		Element b = G2.newRandomElement().getImmutable();
		
		assertEquals(
				pairing.pairing(a.square(), b),
				pairing.pairing(a, b.square())
				);
		
		// Bilinearity check 2
		assertEquals(
				pairing.pairing(a.pow(new BigInteger("2")), b),
				pairing.pairing(a, b.pow(new BigInteger("2")))
				);
	}
}
