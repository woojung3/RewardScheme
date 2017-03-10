package kr.ac.mju.islab;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import it.unisa.dia.gas.jpbc.*;
import it.unisa.dia.gas.plaf.jpbc.pairing.PairingFactory;
import it.unisa.dia.gas.plaf.jpbc.util.io.Base64;

import kr.ac.mju.islab.secParam.*;

/**
 * RewardScheme class implements 'Privacy-Preserving Reward System for Cloudlet' 
 * developed by Dae Hyun Yum at August 14, 2016.
 * <p>
 * If you are not interested in detailed implementation,
 * you would better use RewardServer/RewardQuery to make application based on
 * Reward Scheme.
 * <p>
 * RewardServer/RewardQuery is a concrete implementation the Reward Scheme.
 * 
 * @author jwlee
 * @version 1.0.0
 * @since 2017-03-08
 */
public class RewardScheme {
	public CurveName curveName;
	public Hash hash;
	private Pairing pairing;
	@SuppressWarnings("rawtypes")
	public Field Zr, G1, G2;
	public Element g1, g2, x, y;	// x should only be known to master.

	/*
	 * L is synchronized in the same object. 
	 * Be careful that L is not synchronized between different objects.
	 */
	public Set<Element> L = Collections.synchronizedSet(new HashSet<Element>());

	/**
	 * Class constructor - default set to curve type a (symmetric) and SHA256.
	 */
	public RewardScheme() {
		this(CurveName.a, HashName.SHA256);
	}
	/**
	 * Class constructor specifying curve name and hash name.
	 * <p>
	 * Supported curves: a, a1, d159, d201, d224, e, f, g149. <br>
	 * Supported hashes: SHA1, SHA256, SHA384, SHA512.
	 * 
	 * @param curveName the name of elliptic curve
	 * @param hashName the name of hash function
	 */
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
		
		/*
		 * Generator generation.
		 */
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
		y = g2.powZn(x).getImmutable();

		try {
			this.hash = new Hash(hashName);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Configure y, g1, g2 same as those of Master's so that RewardScheme object
	 * of Helper can properly communicate with RewardScheme object of Master.
	 * This is same as setting y, g1, g2 individually.
	 * 
	 * @param y Master's public key
	 * @param g1 generator of G1 that Master is using
	 * @param g2 generator of G2 that Master is using
	 */
	public void configureAsHelper(Element y, Element g1, Element g2) {
		this.y = y;
		this.g1 = g1;
		this.g2 = g2;
	}
	
	/**
	 * Returns random serial number s, random number r, and 
	 * computed value h to Helper.
	 * Helper will use there data in recIssue Process.
	 * Especially, h will be sent to Master to generate psi.
	 * @return the serial number s, random number r, and computed h in Element[] form with corresponding order.
	 */
	public Element[] recIssueHelperPre() {
		Element s = Zr.newRandomElement().getImmutable();	// Select s.
		while (L.contains(s) == true){
			s = Zr.newRandomElement().getImmutable();
		}
		
		Element r = Zr.newRandomElement().getImmutable();	// Select r.
		byte[] digest = hash.getByteDigest(s.toBytes());

		Element h = g1.powZn(r)	// Select h <- g1^r*H(s).
				.mul(G1.newElement().setFromHash(digest, 0, digest.length)).getImmutable();
		
		Element[] rtn = new Element[3];
		rtn[0] = s;
		rtn[1] = r;
		rtn[2] = h;

		return rtn;
	}
	
	/**
	 * Returns element psi which master generates in recIssue Process
	 * Helper provides h, and later generates receipt sigma with returned value psi.
	 * 
	 * @param h the element generated and provided by Helper
	 * @return the mid-computation element psi for Helper
	 */
	public Element recIssueMaster(Element h) {
		// Master.
		Element psi = h.powZn(x).getImmutable();	// Select psi <- h^x.
		
		return psi;
	}

	/**
	 * Returns the receipt sigma for Helper.
	 * sigma, along with random number s and verification key y,
	 * will be used in verification process.
	 * 
	 * @param r mid-computation value generate by Helper
	 * @param psi mid-computation value generated by Master
	 * @param y the verification key vk
	 * @return the receipt sigma for Helper
	 */
	public Element recIssueHelperPost(Element r, Element psi, Element y) {
		Element sigma = y.powZn(r.negate()).mul(psi).getImmutable();	// Select sigma <- y^(-r)*psi.
		
		return sigma;
	}
	
	/**
	 * Returns boolean value which indicates the validness of the signature sigma.
	 * 
	 * @param sigma the receipt generated by Helper
	 * @param s the random number generated by Helper
	 * @param y the verification key vk
	 * @return validness of the signature sigma
	 */
	public boolean verify(Element sigma, Element s, Element y) {
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
	
	/**
	 * Returns aggregated sigma from provided signature sigmas.
	 * 
	 * @param sigmaList the arraylist of signature sigma
	 * @return aggregated signature sigma
	 */
	public Element aggregate(List<Element> sigmaList) {
		Element sigmaAgg = G2.newOneElement();
		for (int i=0; i<sigmaList.size(); i++) {
			sigmaAgg.mul(sigmaList.get(i));
		}
		
		return sigmaAgg;
	}
	
	/**
	 * Returns boolean value which indicates the validness of the 
	 * aggregated signature sigma.
	 * 
	 * @param sigmaAgg the aggregated signature sigma
	 * @param sList the arraylist of random serial number s
	 * @param yList the arraylist of verification key vk, or y
	 * @return validness of the aggregated signature sigma
	 */
	public boolean aggVerify(Element sigmaAgg, List<Element> sList, List<Element> yList){
		Element comp = pairing.getGT().newOneElement();
		for (int i=0; i<sList.size(); i++) {
			if (L.contains(sList.get(i)) == true){
				return false;
			}
			byte[] digest = hash.getByteDigest(sList.get(i).toBytes());
			comp.mul(pairing.pairing(G1.newElement().setFromHash(digest, 0, digest.length), yList.get(i)));
		}
		
		if (comp.isEqual(pairing.pairing(sigmaAgg, g2))) {
			return true;
		}
		else {
			return false;
		}
	}

	/**
	 * Hash class provides several hash-related methods which is dedicated to RewardScheme class.
	 * You don't need to use this class unless you have to modify/inherit RewardScheme class.
	 * 
	 * @author jwlee
	 */
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