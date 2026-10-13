package com.omerceren.barkodu
import org.junit.Test
import org.junit.Assert.*
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class CargoModelTest {
    @Test fun individualDuplicatesAndUndo() {
        val m=CargoModel();m.selected=2;val first=m.add("7312345678");val second=m.add("7312345678")
        assertEquals(2,m.count(2));assertNotEquals(first.id,second.id);m.remove(first);assertEquals(second.id,m.items.first().id)
        m.undo();assertEquals(first.id,m.items[1].id);m.undo();assertEquals(1,m.items.size);assertEquals(first.id,m.items.first().id)
    }
    @Test fun prefixesAndUnknown() {
        CargoModel.PREFIXES.forEachIndexed {cargo,values->values.forEach {assertEquals(cargo,CargoModel.classify(it+"123456789",cargo))}}
        assertEquals(4,CargoModel.classify("4171234567",0));assertEquals(4,CargoModel.classify("731234",3));assertFalse(CargoModel.valid("https://example.com"))
    }
    @Test fun undoOrderAndCounts() {
        val m=CargoModel();m.selected=2;m.add("7312345678");m.add("7398765432");m.add("7312345678")
        assertEquals(3,m.items.size);m.undo();assertEquals("7398765432",m.items.first().code)
        m.remove(m.items.first());m.undo();assertEquals(2,m.items.size);m.clear();assertTrue(m.items.isEmpty());m.undo();assertEquals(2,m.items.size)
    }
    @Test fun batchUndoPreservesNewScans() {
        val m=CargoModel();m.selected=2;val a=m.add("731111");val b=m.add("732222");val c=m.add("733333")
        val removed=m.removeMany(setOf(a.id,c.id));assertEquals(listOf(b.id),m.items.map {it.id})
        val fresh=m.add("734444");m.restore(removed);assertEquals(setOf(a.id,b.id,c.id,fresh.id),m.items.map {it.id}.toSet())
    }
    @Test fun carriersPreserveStringData() {
        val m=CargoModel();m.selected=0;m.add("4161125527");m.selected=1;m.add("4161125527")
        assertEquals(2,m.items.size);assertEquals(1,m.count(0));assertEquals(1,m.count(1));assertTrue(m.text().contains("[JET]"))
        m.selected=2;m.add("00Ab1234");assertTrue(m.text().contains("00Ab1234"))
    }
    @Test fun xlsxPreservesLeadingZerosAndCase() {
        val m=CargoModel();m.selected=2;m.add("001Ab234");val out=ByteArrayOutputStream();XlsxExport.write(m,out)
        var sheets=0;var cell=false;var workbook=false
        ZipInputStream(out.toByteArray().inputStream()).use {zip->
            while(true){val entry=zip.nextEntry?:break;val xml=zip.readBytes().toString(Charsets.UTF_8)
                if(entry.name.startsWith("xl/worksheets/"))sheets++
                if(xml.contains("<t>001Ab234</t>"))cell=true
                if(entry.name=="xl/workbook.xml")workbook=xml.contains("TEX")&&!xml.contains("Unknown")
            }
        }
        assertEquals(4,sheets);assertTrue(cell);assertTrue(workbook)
    }
}
