package eu.rekisoft.android.editmail

import org.minidns.dnsmessage.DnsMessage
import org.minidns.dnsqueryresult.DnsQueryResult

internal class DohResult(query: DnsMessage, data: ByteArray?) :
    DnsQueryResult(QueryMethod.tcp, query, DnsMessage(data))