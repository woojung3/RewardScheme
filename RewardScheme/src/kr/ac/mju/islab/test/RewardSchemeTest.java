package kr.ac.mju.islab.test;
import kr.ac.mju.islab.RewardScheme;

import it.unisa.dia.gas.jpbc.*;
import it.unisa.dia.gas.plaf.jpbc.pairing.PairingFactory;

import static org.junit.Assert.*;
import org.junit.Test;


public class RewardSchemeTest {

	@Test
	public void testPairing() {
		Pairing pairing = PairingFactory.getPairing("params/mm/ctl13/toy.properties");
	}
}
