package tr.texgo.kargobarkod;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;

public class CargoModelTest {
    @Test public void prefixesAndUnknown(){for(int cargo=0;cargo<4;cargo++)for(String prefix:CargoModel.PREFIXES[cargo])assertEquals(cargo,CargoModel.classify(prefix+"123456789",cargo));assertEquals(4,CargoModel.classify("4171234567",0));assertEquals(4,CargoModel.classify("7312345678",3));assertFalse(CargoModel.valid("https://example.com"));}
    @Test public void undoRestoresOrderAndCount(){CargoModel m=new CargoModel();m.selected=2;m.add("7312345678");m.add("7398765432");m.add("7312345678");assertEquals(2,m.items.get(0).quantity);m.undo();assertEquals("7398765432",m.items.get(0).code);assertEquals(1,m.items.get(1).quantity);m.remove(m.items.get(0));m.undo();assertEquals(2,m.items.size());m.clear();assertEquals(0,m.items.size());m.undo();assertEquals(2,m.items.size());}
    @Test public void carrierChangesPreserveItems(){CargoModel m=new CargoModel();m.selected=0;m.add("4161125527");m.selected=1;m.add("4161125527");assertEquals(2,m.items.size());assertEquals(1,m.count(0));assertEquals(1,m.count(1));assertTrue(m.text().contains("[HepsiJet]"));}
    @Test public void realXlsxPreservesLeadingZeros()throws Exception {CargoModel m=new CargoModel();m.selected=2;m.add("0012345678");ByteArrayOutputStream out=new ByteArrayOutputStream();XlsxExport.write(m,out);boolean cell=false,workbook=false;int sheets=0;try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))){ZipEntry e;byte[] buffer=new byte[8192];while((e=zip.getNextEntry())!=null){ByteArrayOutputStream bytes=new ByteArrayOutputStream();int n;while((n=zip.read(buffer))!=-1)bytes.write(buffer,0,n);String xml=bytes.toString(StandardCharsets.UTF_8);if(e.getName().startsWith("xl/worksheets/"))sheets++;if(xml.contains("t=\"inlineStr\"><is><t>0012345678"))cell=true;if(e.getName().equals("xl/workbook.xml")&&xml.contains("Unknown"))workbook=true;}}assertEquals(5,sheets);assertTrue(cell);assertTrue(workbook);}
}
