package kr.ac.mju.islab;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import it.unisa.dia.gas.jpbc.*;
import it.unisa.dia.gas.plaf.jpbc.pairing.PairingFactory;
import it.unisa.dia.gas.plaf.jpbc.util.io.Base64;

import kr.ac.mju.islab.secParam.*;

public class RewardScheme {
	public CurveName curveName;
	public Hash hash;
	private Pairing pairing;
	@SuppressWarnings("rawtypes")
	private Field Zr, G1, G2;
	public Element g1, g2, y;
	private Element x;

	/*
	 * L is synchronized in the same object. Be careful that L is not synchronized
	 * between different objects.
	 */
	public Set<Element> L = Collections.synchronizedSet(new HashSet<Element>());

	public RewardScheme() {
		this(CurveName.a, HashName.SHA256);
	}
	public RewardScheme(CurveName curveName, HashName hashName) {
		// Setup
		this.curveName = curveName;

		switch(curveName){
			case a:
				pairing = PairingFactory.getPairing("params/curves/a.properties");
				break;
			case a1:
				pairing = PairingFactory.getPairing("params/curves/a1.properties");
				break;
			case d159:
				pairing = PairingFactory.getPairing("params/curves/d159.properties");
				break;
			case d201:
				pairing = PairingFactory.getPairing("params/curves/d201.properties");
				break;
			case d224:
				pairing = PairingFactory.getPairing("params/curves/d224.properties");
				break;
			case e:
				pairing = PairingFactory.getPairing("params/curves/e.properties");
				break;
			case f:
				pairing = PairingFactory.getPairing("params/curves/f.properties");
				break;
			case g149:
				pairing = PairingFactory.getPairing("params/curves/g149.properties");
				break;
		}
		PairingFactory.getInstance().setUsePBCWhenPossible(true);

		Zr = pairing.getZr();
		G1 = pairing.getG1();
		G2 = pairing.getG2();
		
		this.g1 = G1.newRandomElement().getImmutable();
		if (pairing.isSymmetric()) {
			this.g2 = g1.duplicate().getImmutable();
		}
		else {
			this.g2 = G2.newRandomElement().getImmutable();
		}
		
		/*
		 * Key Generation.
		 * x = sk
		 * y = vk
		 */
		x = Zr.newRandomElement().getImmutable();
		y = g2.powZn(x);

		try {
			this.hash = new Hash(hashName);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
	}
	
	// This is an alias for Master-Helper sequence.
	public Element[] recIssuingProcess() {
		// Helper.
		Element s = Zr.newRandomElement().getImmutable();	// Select s.
		while (L.contains(s) == true){
			s = Zr.newRandomElement().getImmutable();
		}
		
		Element r = Zr.newRandomElement().getImmutable();	// Select r.
		byte[] digest = hash.getByteDigest(s.toBytes());

		Element h = g1.powZn(r)	// Select h <- g1^r*H(s).
				.mul(G1.newElement().setFromHash(digest, 0, digest.length)).getImmutable();
		
		// Master.
		Element psi = h.powZn(x).getImmutable();	// Select psi <- h^x.
		
		// Again, Helper.
		Element sigma = y.powZn(r.negate()).mul(psi).getImmutable();	// Select sigma <- y^(-r)*psi.
		
		Element rtn[] = new Element[2];
		rtn[0] = sigma;
		rtn[1] = s;
		return rtn;
	}
	
	public boolean verification(Element sigma, Element s) {
		if (s.getLengthInBytes() > Zr.getLengthInBytes()) {
			return false;
		}
		else if (L.contains(s)) {
			return false;
		}
		else {
			L.add(s);
		}
		
		byte[] digest = hash.getByteDigest(s.toBytes());
		Element hs = G1.newElement().setFromHash(digest, 0, digest.length).getImmutable();
		if (pairing.pairing(sigma, g2).equals(pairing.pairing(hs, y))) {
			return true;
		}
		else {
			return false;
		}
	}
	
	public void aggregation() {
		
	}
	
	public void aggVerification(){
		
	}

	public class Hash {
		public HashName hashName;
		private MessageDigest md;

		public Hash(HashName hashName) throws NoSuchAlgorithmException {
			this.hashName = hashName;
			switch(hashName){
				case SHA1:
					md = MessageDigest.getInstance("SHA-1");
					break;
				case SHA256:
					md = MessageDigest.getInstance("SHA-256");
					break;
				case SHA384:
					md = MessageDigest.getInstance("SHA-384");
					break;
				case SHA512:
					md = MessageDigest.getInstance("SHA-512");
					break;
			}
		}
		
		public String getBase64Digest(String plainText) {
			return getBase64Digest(plainText.getBytes(Charset.forName("UTF-8")));
		}
		public String getBase64Digest(byte[] bytes) {
			md.update(bytes, 0, bytes.length);
			return Base64.encodeBytes(md.digest());
		}

		public byte[] getByteDigest(String plainText) {
			return getByteDigest(plainText.getBytes(Charset.forName("UTF-8")));
		}
		public byte[] getByteDigest(byte[] bytes) {
			md.update(bytes, 0, bytes.length);
			return md.digest();
		}

		public String getHexDigest(String plainText) {
			return getHexDigest(plainText.getBytes(Charset.forName("UTF-8")));
		}
		public String getHexDigest(byte[] bytes) {
			md.update(bytes, 0, bytes.length);
			bytes = md.digest();

			StringBuilder hexString = new StringBuilder();

		    for (int i = 0; i < bytes.length; i++) {
		        String hex = Integer.toHexString(0xFF & bytes[i]);
		        if (hex.length() == 1) {
		            hexString.append('0');
		        }
		        hexString.append(hex);
		    }

		    return hexString.toString();
		}
	}
}