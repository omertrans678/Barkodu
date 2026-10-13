package com.omerceren.barkodu
import org.junit.Test
import org.junit.Assert.*

class CarrierRulesTest {
    @Test fun confirmedLengthOnlyAppliesToDhlJet416417() {
        val rules=CarrierRules()
        for(carrier in 0..1)for(prefix in listOf("416","417")) {
            assertTrue(rules.validLength(prefix+"1215728",carrier))
            assertFalse(rules.validLength(prefix+"12157278",carrier))
            assertFalse(rules.validLength(prefix+"121572",carrier))
        }
        assertTrue(rules.validLength("HTS1234",0));assertTrue(rules.validLength("HTS123456789",1))
        assertTrue(rules.validLength("731234",2));assertTrue(rules.validLength("721234567890",3))
        assertTrue(rules.validLength("416123456789",2))
    }
    @Test fun prefixWhitelistIsCarrierScoped() {
        val rules=CarrierRules();assertFalse(rules.matches("4171215728",0))
        rules.setPrefixes(0,listOf("416","417","hts"))
        assertTrue(rules.matches("4171215728",0));assertFalse(rules.matches("4171215728",1));assertTrue(rules.matches("HTS001234",0))
        rules.setPrefixes(0,emptyList());assertFalse(rules.matches("4161215728",0))
    }
    @Test fun invalidPrefixCannotReplaceValidRules() {
        val rules=CarrierRules();val before=rules.prefixes(0)
        assertThrows(IllegalArgumentException::class.java){rules.setPrefixes(0,listOf("https://example.com"))}
        assertEquals(before,rules.prefixes(0))
    }
    @Test fun configurableOtherLengthsDoNotAssumeEleven() {
        val rules=CarrierRules();rules.setLengths(2,setOf(6,8))
        assertTrue(rules.validLength("731234",2));assertTrue(rules.validLength("73123456",2));assertFalse(rules.validLength("7312345",2))
    }
}
