package com.homedashboard.app.data.remote.caldav

import android.util.Log
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parser for CalDAV XML responses.
 * Handles PROPFIND, REPORT, and Multi-Status responses.
 */
class CalDavXmlParser {

    private val documentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }

    /**
     * Parse a multi-status response containing calendar list.
     */
    fun parseCalendarList(xmlBody: String): CalendarListResponse {
        val doc = parseXml(xmlBody)
        val calendars = mutableListOf<CalDavCalendar>()

        val responses = doc.getElementsByTagNameNS(CalDavNamespaces.DAV, "response")
        for (i in 0 until responses.length) {
            val response = responses.item(i) as? Element ?: continue
            val calendar = parseCalendarResponse(response)
            if (calendar != null) {
                calendars.add(calendar)
            }
        }

        return CalendarListResponse(calendars = calendars)
    }

    /**
     * Parse a sync-collection REPORT response.
     */
    fun parseSyncCollectionResponse(xmlBody: String): SyncCollectionResponse {
        val doc = parseXml(xmlBody)
        val events = mutableListOf<CalDavEventResource>()

        // Parse event responses
        val responses = doc.getElementsByTagNameNS(CalDavNamespaces.DAV, "response")
        for (i in 0 until responses.length) {
            val response = responses.item(i) as? Element ?: continue
            val event = parseEventResponse(response)
            if (event != null) {
                events.add(event)
            }
        }

        // Extract sync-token
        val syncToken = extractSyncToken(doc)

        return SyncCollectionResponse(
            events = events,
            syncToken = syncToken
        )
    }

    /**
     * Parse a calendar-query REPORT response.
     */
    fun parseCalendarQueryResponse(xmlBody: String): CalendarQueryResponse {
        val doc = parseXml(xmlBody)
        val events = mutableListOf<CalDavEventResource>()

        val responses = doc.getElementsByTagNameNS(CalDavNamespaces.DAV, "response")
        for (i in 0 until responses.length) {
            val response = responses.item(i) as? Element ?: continue
            val event = parseEventResponse(response)
            if (event != null && event.icalData != null) {
                events.add(event)
            }
        }

        return CalendarQueryResponse(events = events)
    }

    /**
     * Extract the sync-token from a document.
     */
    fun extractSyncToken(doc: Document): String? {
        // First look at root level
        val syncTokens = doc.getElementsByTagNameNS(CalDavNamespaces.DAV, "sync-token")
        if (syncTokens.length > 0) {
            val token = syncTokens.item(0).textContent?.trim()
            if (!token.isNullOrEmpty()) {
                return token
            }
        }

        // Also check propstat responses
        val propstatTokens = doc.getElementsByTagNameNS(CalDavNamespaces.DAV, "sync-token")
        for (i in 0 until propstatTokens.length) {
            val token = propstatTokens.item(i).textContent?.trim()
            if (!token.isNullOrEmpty()) {
                return token
            }
        }

        return null
    }

    /**
     * Parse a single calendar response element.
     */
    private fun parseCalendarResponse(response: Element): CalDavCalendar? {
        val href = getChildText(response, CalDavNamespaces.DAV, "href") ?: return null

        // Get propstat with 200 OK status
        val propstat = getSuccessfulPropstat(response) ?: return null
        val prop = getFirstChildByTagName(propstat, CalDavNamespaces.DAV, "prop") ?: return null

        // Check if this is a calendar collection
        val resourceType = getFirstChildByTagName(prop, CalDavNamespaces.DAV, "resourcetype")
        val isCalendar = resourceType?.let { rt ->
            getFirstChildByTagName(rt, CalDavNamespaces.CALDAV, "calendar") != null
        } ?: false

        if (!isCalendar) {
            return null
        }

        // Extract calendar properties
        val displayName = getChildText(prop, CalDavNamespaces.DAV, "displayname") ?: "Untitled"
        val ctag = getChildText(prop, CalDavNamespaces.CALENDARSERVER, "getctag")
        val syncToken = getChildText(prop, CalDavNamespaces.DAV, "sync-token")

        // Apple-specific properties
        val color = getChildText(prop, CalDavNamespaces.APPLE, "calendar-color")
        val orderStr = getChildText(prop, CalDavNamespaces.APPLE, "calendar-order")
        val order = orderStr?.toIntOrNull() ?: 0

        // Get supported components
        val supportedComponents = mutableListOf<String>()
        val compSet = getFirstChildByTagName(prop, CalDavNamespaces.CALDAV, "supported-calendar-component-set")
        if (compSet != null) {
            val comps = compSet.getElementsByTagNameNS(CalDavNamespaces.CALDAV, "comp")
            for (i in 0 until comps.length) {
                val comp = comps.item(i) as? Element
                val name = comp?.getAttribute("name")
                if (!name.isNullOrEmpty()) {
                    supportedComponents.add(name)
                }
            }
        }
        if (supportedComponents.isEmpty()) {
            supportedComponents.add("VEVENT")
        }

        return CalDavCalendar(
            href = href,
            displayName = displayName,
            color = color,
            supportedComponents = supportedComponents,
            ctag = ctag,
            syncToken = syncToken,
            order = order
        )
    }

    /**
     * Parse a single event response element.
     */
    private fun parseEventResponse(response: Element): CalDavEventResource? {
        val href = getChildText(response, CalDavNamespaces.DAV, "href") ?: return null

        // Skip collection resources
        if (href.endsWith("/")) {
            return null
        }

        // Check status - 404 means deleted
        val status = getChildText(response, CalDavNamespaces.DAV, "status")
        val statusCode = parseHttpStatus(status)

        // If deleted (404), return resource marked as deleted
        if (statusCode == 404) {
            return CalDavEventResource(
                href = href,
                etag = null,
                icalData = null,
                status = 404
            )
        }

        // Get propstat with 200 OK status
        val propstat = getSuccessfulPropstat(response)
        val prop = if (propstat != null) {
            getFirstChildByTagName(propstat, CalDavNamespaces.DAV, "prop")
        } else null

        // Extract properties
        val etag = prop?.let { getChildText(it, CalDavNamespaces.DAV, "getetag") }
            ?.trim()
            ?.removeSurrounding("\"")

        val icalData = prop?.let { getChildText(it, CalDavNamespaces.CALDAV, "calendar-data") }
            ?.trim()

        return CalDavEventResource(
            href = href,
            etag = etag,
            icalData = icalData,
            status = statusCode
        )
    }

    /**
     * Get the propstat element with a successful (2xx) status.
     */
    private fun getSuccessfulPropstat(response: Element): Element? {
        val propstats = response.getElementsByTagNameNS(CalDavNamespaces.DAV, "propstat")
        for (i in 0 until propstats.length) {
            val propstat = propstats.item(i) as? Element ?: continue
            val status = getChildText(propstat, CalDavNamespaces.DAV, "status")
            val statusCode = parseHttpStatus(status)
            if (statusCode in 200..299) {
                return propstat
            }
        }
        return null
    }

    /**
     * Parse HTTP status line (e.g., "HTTP/1.1 200 OK") to status code.
     */
    private fun parseHttpStatus(status: String?): Int {
        if (status == null) return 200

        // Format: "HTTP/1.1 200 OK"
        val parts = status.trim().split(" ")
        return if (parts.size >= 2) {
            parts[1].toIntOrNull() ?: 200
        } else {
            200
        }
    }

    /**
     * Parse XML string to Document.
     */
    private fun parseXml(xmlBody: String): Document {
        try {
            val builder = documentBuilderFactory.newDocumentBuilder()
            return builder.parse(InputSource(StringReader(xmlBody)))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse XML", e)
            throw CalDavParseException("Failed to parse XML response", e)
        }
    }

    /**
     * Get text content of a child element.
     */
    private fun getChildText(parent: Element, namespace: String, localName: String): String? {
        val elements = parent.getElementsByTagNameNS(namespace, localName)
        return if (elements.length > 0) {
            elements.item(0).textContent?.trim()
        } else {
            null
        }
    }

    /**
     * Get first child element by tag name.
     */
    private fun getFirstChildByTagName(parent: Element, namespace: String, localName: String): Element? {
        val elements = parent.getElementsByTagNameNS(namespace, localName)
        return if (elements.length > 0) {
            elements.item(0) as? Element
        } else {
            null
        }
    }

    // ==================== XML Building Utilities ====================

    /**
     * Build PROPFIND request body for calendar discovery.
     */
    fun buildCalendarListPropfind(): String {
        return """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav" xmlns:cs="http://calendarserver.org/ns/" xmlns:a="http://apple.com/ns/ical/">
  <d:prop>
    <d:resourcetype/>
    <d:displayname/>
    <d:getetag/>
    <d:sync-token/>
    <cs:getctag/>
    <c:supported-calendar-component-set/>
    <a:calendar-color/>
    <a:calendar-order/>
  </d:prop>
</d:propfind>"""
    }

    /**
     * Build sync-collection REPORT request body.
     */
    fun buildSyncCollectionReport(syncToken: String?): String {
        val tokenElement = if (syncToken != null) {
            "<d:sync-token>$syncToken</d:sync-token>"
        } else {
            "<d:sync-token/>"
        }

        return """<?xml version="1.0" encoding="utf-8"?>
<d:sync-collection xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
  $tokenElement
  <d:sync-level>1</d:sync-level>
  <d:prop>
    <d:getetag/>
    <c:calendar-data/>
  </d:prop>
</d:sync-collection>"""
    }

    /**
     * Build calendar-query REPORT request body with time range filter.
     */
    fun buildCalendarQueryReport(
        startTime: String,  // Format: 20260101T000000Z
        endTime: String     // Format: 20260131T235959Z
    ): String {
        return """<?xml version="1.0" encoding="utf-8"?>
<c:calendar-query xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
  <d:prop>
    <d:getetag/>
    <c:calendar-data/>
  </d:prop>
  <c:filter>
    <c:comp-filter name="VCALENDAR">
      <c:comp-filter name="VEVENT">
        <c:time-range start="$startTime" end="$endTime"/>
      </c:comp-filter>
    </c:comp-filter>
  </c:filter>
</c:calendar-query>"""
    }

    /**
     * Build calendar-multiget REPORT request body.
     */
    fun buildCalendarMultigetReport(eventHrefs: List<String>): String {
        val hrefElements = eventHrefs.joinToString("\n") { href ->
            "  <d:href>$href</d:href>"
        }

        return """<?xml version="1.0" encoding="utf-8"?>
<c:calendar-multiget xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
  <d:prop>
    <d:getetag/>
    <c:calendar-data/>
  </d:prop>
$hrefElements
</c:calendar-multiget>"""
    }

    companion object {
        private const val TAG = "CalDavXmlParser"
    }
}
