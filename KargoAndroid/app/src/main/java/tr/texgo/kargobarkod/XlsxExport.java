package tr.texgo.kargobarkod;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;

public final class XlsxExport {
    private static final String NS="http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static String escape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
    private static void entry(ZipOutputStream zip,String path,String text)throws IOException{zip.putNextEntry(new ZipEntry(path));zip.write(text.getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
    private static String cell(String ref,String value){return "<c r=\""+ref+"\" t=\"inlineStr\"><is><t>"+escape(value)+"</t></is></c>";}
    public static void write(CargoModel model,OutputStream output)throws IOException{
        try(ZipOutputStream zip=new ZipOutputStream(output)){
            StringBuilder types=new StringBuilder("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>");
            StringBuilder workbook=new StringBuilder("<workbook xmlns=\""+NS+"\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>");
            StringBuilder rels=new StringBuilder("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
            for(int cargo=0;cargo<CargoModel.NAMES.length;cargo++){
                int id=cargo+1;String path="worksheets/sheet"+id+".xml";
                types.append("<Override PartName=\"/xl/").append(path).append("\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
                workbook.append("<sheet name=\"").append(escape(CargoModel.NAMES[cargo].replace('/','-'))).append("\" sheetId=\"").append(id).append("\" r:id=\"rId").append(id).append("\"/>");
                rels.append("<Relationship Id=\"rId").append(id).append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"").append(path).append("\"/>");
                StringBuilder sheet=new StringBuilder("<worksheet xmlns=\""+NS+"\"><cols><col min=\"1\" max=\"1\" width=\"35\" customWidth=\"1\"/><col min=\"2\" max=\"2\" width=\"16\" customWidth=\"1\"/></cols><sheetData><row r=\"1\">"+cell("A1","Barcode")+cell("B1","Quantity")+"</row>");
                int row=2;for(CargoModel.Item x:model.items)if(x.cargo==cargo){sheet.append("<row r=\"").append(row).append("\">").append(cell("A"+row,x.code)).append("<c r=\"B").append(row).append("\"><v>").append(x.quantity).append("</v></c></row>");row++;}
                sheet.append("</sheetData></worksheet>");entry(zip,"xl/"+path,sheet.toString());
            }
            entry(zip,"[Content_Types].xml",types.append("</Types>").toString());
            entry(zip,"_rels/.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            entry(zip,"xl/workbook.xml",workbook.append("</sheets></workbook>").toString());
            entry(zip,"xl/_rels/workbook.xml.rels",rels.append("</Relationships>").toString());
        }
    }
}
