package com.omerceren.barkodu

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object XlsxExport {
    private const val NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun cell(ref: String, value: String) = """<c r="$ref" t="inlineStr"><is><t>${escape(value)}</t></is></c>"""
    fun write(model: CargoModel, output: OutputStream) {
        ZipOutputStream(output).use {zip ->
            fun entry(path: String, text: String) {zip.putNextEntry(ZipEntry(path)); zip.write(text.toByteArray(Charsets.UTF_8)); zip.closeEntry()}
            val types = StringBuilder("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
            val workbook = StringBuilder("""<workbook xmlns="$NS" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
            val rels = StringBuilder("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
            CargoModel.NAMES.forEachIndexed {cargo, name ->
                val id = cargo + 1; val path = "worksheets/sheet$id.xml"
                types.append("""<Override PartName="/xl/$path" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
                workbook.append("""<sheet name="${escape(name)}" sheetId="$id" r:id="rId$id"/>""")
                rels.append("""<Relationship Id="rId$id" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="$path"/>""")
                val sheet = StringBuilder("""<worksheet xmlns="$NS"><cols><col min="1" max="1" width="35" customWidth="1"/><col min="2" max="2" width="16" customWidth="1"/></cols><sheetData><row r="1">${cell("A1", "Barcode")}${cell("B1", "Quantity")}</row>""")
                model.items.filter {it.cargo == cargo}.forEachIndexed {index, item ->
                    val row = index + 2
                    sheet.append("""<row r="$row">${cell("A$row", item.code)}<c r="B$row"><v>${item.quantity}</v></c></row>""")
                }
                entry("xl/$path", sheet.append("</sheetData></worksheet>").toString())
            }
            entry("[Content_Types].xml", types.append("</Types>").toString())
            entry("_rels/.rels", """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            entry("xl/workbook.xml", workbook.append("</sheets></workbook>").toString())
            entry("xl/_rels/workbook.xml.rels", rels.append("</Relationships>").toString())
        }
    }
}
