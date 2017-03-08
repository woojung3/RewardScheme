package kr.ac.mju.islab.test;

import kr.ac.mju.islab.RewardScheme;
import kr.ac.mju.islab.secParam.CurveName;
import kr.ac.mju.islab.secParam.HashName;

import it.unisa.dia.gas.jpbc.*;
import it.unisa.dia.gas.plaf.jpbc.pairing.PairingFactory;

import static org.junit.Assert.*;

import java.math.BigInteger;

import org.junit.Test;


public class RewardSchemeTest {
	
	@Test
	public void issuVeriCheck() {
		// Setup
		RewardScheme rewardS = new RewardScheme();
		
		Element rtn[] = rewardS.recIssuingProcess();
		assertEquals(rewardS.verification(rtn[0], rtn[1]), true);
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
	 * Belows are jPBC testing. If you are new to (j)PBC, you can use these as a 
	 * simple self-explained manual.
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
		 * DO NOT FORGET TO DUPLICATE THE DATA! ALL METHODS CHANGES 
		 * THE VARIABLE ITSELF!
		 */
		// Bilinearity check
		Element a = G1.newRandomElement();
		Element b = G2.newRandomElement();
		
		assertEquals(
				pairing.pairing(a.duplicate().square(), b),
				pairing.pairing(a, b.duplicate().square())
				);
		
		// Bilinearity check 2
		assertEquals(
				pairing.pairing(a.duplicate().pow(new BigInteger("2")), b),
				pairing.pairing(a, b.duplicate().pow(new BigInteger("2")))
				);
	}
}
