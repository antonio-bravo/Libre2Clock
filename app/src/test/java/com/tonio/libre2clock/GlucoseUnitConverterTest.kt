package com.tonio.libre2clock

import com.tonio.libre2clock.data.model.GlucoseUnit
import com.tonio.libre2clock.util.GlucoseUnitConverter
import org.junit.Assert.assertEquals
import org.junit.Test

class GlucoseUnitConverterTest {

    @Test
    fun formatValueConvertsMgDlAndMmolLAccurately() {
        assertEquals("180", GlucoseUnitConverter.formatValue(180, GlucoseUnit.MG_DL))
        assertEquals("10.0", GlucoseUnitConverter.formatValue(180, GlucoseUnit.MMOL_L))
        assertEquals("3.9", GlucoseUnitConverter.formatValue(70, GlucoseUnit.MMOL_L))
        assertEquals("---", GlucoseUnitConverter.formatValue(0, GlucoseUnit.MMOL_L))
    }

    @Test
    fun formatDualValueFormatsBothRawAndCalibratedInSelectedUnit() {
        assertEquals("120(110)", GlucoseUnitConverter.formatDualValue(120, 110, GlucoseUnit.MG_DL))
        assertEquals("6.7(6.1)", GlucoseUnitConverter.formatDualValue(120, 110, GlucoseUnit.MMOL_L))
        assertEquals("6.7", GlucoseUnitConverter.formatDualValue(120, 120, GlucoseUnit.MMOL_L))
        assertEquals("mg/dL", GlucoseUnitConverter.unitLabel(GlucoseUnit.MG_DL))
        assertEquals("mmol/L", GlucoseUnitConverter.unitLabel(GlucoseUnit.MMOL_L))
    }
}
