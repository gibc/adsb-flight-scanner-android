package fi.iki.elonen

import fi.iki.elonen.NanoHTTPD.ResponseException
import java.io.*
import java.net.*
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.*

/**
 * A simple, tiny, nicely embeddable HTTP server in Java
 *
 *
 *
 *
 * NanoHTTPD
 *
 *
 *
 * Copyright (c) 2012-2013 by Paul S. Hawke, 2001,2005-2013 by Jarno Elonen,
 * 2010 by Konstantinos Togias
 *
 *
 *
 *
 * **Features + limitations: **
 *
 *
 *
 *  * Only one Java file
 *  * Java 5 compatible
 *  * Released as open source, Modified BSD licence
 *  * No fixed config files, logging, authorization etc. (Implement yourself if
 * you need them.)
 *  * Supports parameter parsing of GET and POST methods (+ rudimentary PUT
 * support in 1.25)
 *  * Supports both dynamic content and file serving
 *  * Supports file upload (since version 1.2, 2010)
 *  * Supports partial content (streaming)
 *  * Supports ETags
 *  * Never caches anything
 *  * Doesn't limit bandwidth, request time or simultaneous connections
 *  * Default code serves files and shows all HTTP parameters and headers
 *  * File server supports directory listing, index.html and index.htm
 *  * File server supports partial content (streaming)
 *  * File server supports ETags
 *  * File server does the 301 redirection trick for directories without '/'
 *  * File server supports simple skipping for files (continue download)
 *  * File server serves also very long files without memory overhead
 *  * Contains a built-in list of most common mime types
 *  * All header names are converted lowercase so they don't vary between
 * browsers/clients
 *
 *
 *
 *
 *
 *
 *
 * **How to use: **
 *
 *
 *
 *  * Subclass and implement serve() and embed to your own program
 *
 *
 *
 *
 *
 * See the separate "LICENSE.md" file for the distribution license (Modified BSD
 * licence)
 */
abstract class NanoHTTPD(private val hostname: String?, private val myPort: Int) {
    private var myServerSocket: ServerSocket? = null
    private val openConnections: MutableSet<Socket> = HashSet()
    private var myThread: Thread? = null

    /**
     * Pluggable strategy for asynchronously executing requests.
     */
    private var asyncRunner: AsyncRunner? = null

    /**
     * Pluggable strategy for creating and cleaning up temporary files.
     */
    private var tempFileManagerFactory: TempFileManagerFactory? = null

    /**
     * Constructs an HTTP server on given port.
     */
    constructor(port: Int) : this(null, port) {}

    /**
     * Start the server.
     *
     * @throws IOException
     * if the socket is in use.
     */
    @Throws(IOException::class)
    open fun start() {
        myServerSocket = ServerSocket()
        myServerSocket!!.bind(hostname?.let { InetSocketAddress(it, myPort) }
                ?: InetSocketAddress(myPort))
        myThread = Thread(Runnable {
            do {
                try {
                    val finalAccept = myServerSocket!!.accept()
                    registerConnection(finalAccept)
                    finalAccept.soTimeout = SOCKET_READ_TIMEOUT
                    val inputStream = finalAccept
                            .getInputStream()
                    asyncRunner!!.exec(Runnable {
                        var outputStream: OutputStream? = null
                        try {
                            outputStream = finalAccept
                                    .getOutputStream()
                            val tempFileManager = tempFileManagerFactory!!
                                    .create()
                            val session = HTTPSession(
                                    tempFileManager, inputStream,
                                    outputStream, finalAccept
                                    .inetAddress)
                            while (!finalAccept.isClosed) {
                                session.execute()
                            }
                        } catch (e: Exception) {
                            // When the socket is closed by the client,
                            // we throw our own SocketException
                            // to break the "keep alive" loop above.
                            if (!(e is SocketException && ("NanoHttpd Shutdown"
                                            == e.message))) {
                                e.printStackTrace()
                            }
                        } finally {
                            safeClose(outputStream)
                            safeClose(inputStream)
                            safeClose(finalAccept)
                            unRegisterConnection(finalAccept)
                        }
                    })
                } catch (e: IOException) {
                }
            } while (!myServerSocket!!.isClosed)
        })
        myThread!!.isDaemon = true
        myThread!!.name = "NanoHttpd Main Listener"
        myThread!!.start()
    }

    /**
     * Stop the server.
     */
    open fun stop() {
        try {
            safeClose(myServerSocket)
            closeAllConnections()
            if (myThread != null) {
                myThread!!.join()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Registers that a new connection has been set up.
     *
     * @param socket
     * the [Socket] for the connection.
     */
    @Synchronized
    fun registerConnection(socket: Socket) {
        openConnections.add(socket)
    }

    /**
     * Registers that a connection has been closed
     *
     * @param socket
     * the [Socket] for the connection.
     */
    @Synchronized
    fun unRegisterConnection(socket: Socket?) {
        openConnections.remove(socket)
    }

    /**
     * Forcibly closes all connections that are open.
     */
    @Synchronized
    fun closeAllConnections() {
        for (socket in openConnections) {
            safeClose(socket)
        }
    }

    val listeningAddress: InetAddress?
        get() = if (myServerSocket == null) null else myServerSocket!!.inetAddress

    val listeningPort: Int
        get() = if (myServerSocket == null) -1 else myServerSocket!!.localPort

    fun wasStarted(): Boolean {
        return myServerSocket != null && myThread != null
    }

    val isAlive: Boolean
        get() = wasStarted() && !myServerSocket!!.isClosed && myThread!!.isAlive

    /**
     * Override this to customize the server.
     *
     *
     *
     *
     * (By default, this delegates to serveFile() and allows directory listing.)
     *
     * @param uri
     * Percent-decoded URI without parameters, for example
     * "/index.cgi"
     * @param method
     * "GET", "POST" etc.
     * @param parms
     * Parsed, percent decoded parameters from URI and, in case of
     * POST, data.
     * @param headers
     * Header entries, percent decoded
     * @return HTTP response, see class Response for details
     */
    @Deprecated("")
    open fun serve(uri: String?, method: Method?,
                   headers: Map<String?, String?>?, parms: Map<String, String?>?,
                   files: Map<String, String>?): Response? {
        return Response(Response.Status.NOT_FOUND, MIME_PLAINTEXT,
                "Not Found")
    }

    /**
     * Override this to customize the server.
     *
     *
     *
     *
     * (By default, this delegates to serveFile() and allows directory listing.)
     *
     * @param session
     * The HTTP session
     * @return HTTP response, see class Response for details
     */
    fun serve(session: IHTTPSession): Response? {
        val files: MutableMap<String, String> = HashMap()
        val method = session.method
        if (Method.PUT == method || Method.POST == method) {
            try {
                session.parseBody(files)
            } catch (ioe: IOException) {
                return Response(Response.Status.INTERNAL_ERROR,
                        MIME_PLAINTEXT, "SERVER INTERNAL ERROR: IOException: "
                        + ioe.message)
            } catch (re: ResponseException) {
                return Response(re.status, MIME_PLAINTEXT,
                        re.message)
            }
        }
        val parms = session.parms
        parms!![QUERY_STRING_PARAMETER] = session.queryParameterString
        return serve(session.uri, method, session.headers, parms,
                files)
    }

    /**
     * Decode percent encoded `String` values.
     *
     * @param str
     * the percent encoded `String`
     * @return expanded form of the input, for example "foo%20bar" becomes
     * "foo bar"
     */
    protected fun decodePercent(str: String?): String? {
        var decoded: String? = null
        try {
            decoded = URLDecoder.decode(str, "UTF8")
        } catch (ignored: UnsupportedEncodingException) {
        }
        return decoded
    }

    /**
     * Decode parameters from a URL, handing the case where a single parameter
     * name might have been supplied several times, by return lists of values.
     * In general these lists will contain a single element.
     *
     * @param parms
     * original **NanoHttpd** parameters values, as passed to the
     * `serve()` method.
     * @return a map of `String` (parameter name) to
     * `List<String>` (a list of the values supplied).
     */
    protected fun decodeParameters(
            parms: Map<String?, String?>): Map<String, MutableList<String>?> {
        return this.decodeParameters(parms[QUERY_STRING_PARAMETER])
    }

    /**
     * Decode parameters from a URL, handing the case where a single parameter
     * name might have been supplied several times, by return lists of values.
     * In general these lists will contain a single element.
     *
     * @param queryString
     * a query string pulled from the URL.
     * @return a map of `String` (parameter name) to
     * `List<String>` (a list of the values supplied).
     */
    protected fun decodeParameters(queryString: String?): Map<String, MutableList<String>?> {
        val parms: MutableMap<String, MutableList<String>?> = HashMap()
        if (queryString != null) {
            val st = StringTokenizer(queryString, "&")
            while (st.hasMoreTokens()) {
                val e = st.nextToken()
                val sep = e.indexOf('=')
                val propertyName = if (sep >= 0) decodePercent(
                        e.substring(0, sep))!!.trim { it <= ' ' } else decodePercent(e)!!.trim { it <= ' ' }
                if (!parms.containsKey(propertyName)) {
                    parms[propertyName] = ArrayList()
                }
                val propertyValue = if (sep >= 0) decodePercent(e
                        .substring(sep + 1)) else null
                if (propertyValue != null) {
                    parms[propertyName]!!.add(propertyValue)
                }
            }
        }
        return parms
    }
    // -------------------------------------------------------------------------------
    // //
    //
    // Threading Strategy.
    //
    // -------------------------------------------------------------------------------
    // //
    /**
     * Pluggable strategy for asynchronously executing requests.
     *
     * @param asyncRunner
     * new strategy for handling threads.
     */
    fun setAsyncRunner(asyncRunner: AsyncRunner?) {
        this.asyncRunner = asyncRunner
    }
    // -------------------------------------------------------------------------------
    // //
    //
    // Temp file handling strategy.
    //
    // -------------------------------------------------------------------------------
    // //
    /**
     * Pluggable strategy for creating and cleaning up temporary files.
     *
     * @param tempFileManagerFactory
     * new strategy for handling temp files.
     */
    fun setTempFileManagerFactory(
            tempFileManagerFactory: TempFileManagerFactory?) {
        this.tempFileManagerFactory = tempFileManagerFactory
    }

    /**
     * HTTP Request methods, with the ability to decode a `String`
     * back to its enum value.
     */
    enum class Method {
        GET, PUT, POST, DELETE, HEAD, OPTIONS;

        companion object {
            fun lookup(method: String?): Method? {
                for (m in values()) {
                    if (m.toString().equals(method, ignoreCase = true)) {
                        return m
                    }
                }
                return null
            }
        }
    }

    /**
     * Pluggable strategy for asynchronously executing requests.
     */
    interface AsyncRunner {
        fun exec(code: Runnable?)
    }

    /**
     * Factory to create temp file managers.
     */
    interface TempFileManagerFactory {
        fun create(): TempFileManager
    }
    // -------------------------------------------------------------------------------
    // //
    /**
     * Temp file manager.
     *
     *
     *
     *
     * Temp file managers are created 1-to-1 with incoming requests, to create
     * and cleanup temporary files created as a result of handling the request.
     *
     */
    interface TempFileManager {
        @Throws(Exception::class)
        fun createTempFile(): TempFile
        fun clear()
    }

    /**
     * A temp file.
     *
     *
     *
     *
     * Temp files are responsible for managing the actual temporary storage and
     * cleaning themselves up when no longer needed.
     *
     */
    interface TempFile {
        @Throws(Exception::class)
        fun open(): OutputStream

        @Throws(Exception::class)
        fun delete()
        val name: String
    }

    /**
     * Default threading strategy for NanoHttpd.
     *
     *
     *
     *
     * By default, the server spawns a new Thread for every incoming request.
     * These are set to *daemon* status, and named according to the request
     * number. The name is useful when profiling the application.
     *
     */
    class DefaultAsyncRunner : AsyncRunner {
        private var requestCount: Long = 0
        override fun exec(code: Runnable?) {
            ++requestCount
            val t = Thread(code)
            t.isDaemon = true
            t.name = "NanoHttpd Request Processor (#$requestCount)"
            t.start()
        }
    }

    /**
     * Default strategy for creating and cleaning up temporary files.
     *
     *
     *
     *
     *
     * This class stores its files in the standard location (that is, wherever
     * `java.io.tmpdir` points to). Files are added to an internal
     * list, and deleted when no longer needed (that is, when
     * `clear()` is invoked at the end of processing a request).
     */
    class DefaultTempFileManager : TempFileManager {
        private val tmpdir: String
        private val tempFiles: MutableList<TempFile>

        @Throws(Exception::class)
        override fun createTempFile(): TempFile {
            val tempFile = DefaultTempFile(tmpdir)
            tempFiles.add(tempFile)
            return tempFile
        }

        override fun clear() {
            for (file in tempFiles) {
                try {
                    file.delete()
                } catch (ignored: Exception) {
                }
            }
            tempFiles.clear()
        }

        init {
            tmpdir = System.getProperty("java.io.tmpdir")
            tempFiles = ArrayList()
        }
    }

    /**
     * Default strategy for creating and cleaning up temporary files.
     *
     *
     *
     *
     *
     * [>By default, files are created by `File.createTempFile()`
     * in the directory specified.
     */
    class DefaultTempFile(tempdir: String?) : TempFile {
        private val file: File
        private val fstream: OutputStream

        @Throws(Exception::class)
        override fun open(): OutputStream {
            return fstream
        }

        @Throws(Exception::class)
        override fun delete() {
            safeClose(fstream)
            file.delete()
        }

        override val name: String
            get() = file.absolutePath

        init {
            file = File.createTempFile("NanoHTTPD-", "", File(tempdir))
            fstream = FileOutputStream(file)
        }
    }

    /**
     * HTTP response. Return one of these from serve().
     */
    class Response {
        /**
         * HTTP status code after processing, e.g. "200 OK", HTTP_OK
         */
        var status: IStatus?
            private set

        /**
         * MIME type of content, e.g. "text/html"
         */
        var mimeType: String?

        /**
         * Data of the response, may be null.
         */
        var data: InputStream? = null

        /**
         * Headers for the HTTP response. Use addHeader() to add lines.
         */
        private val header: MutableMap<String, String?>? = HashMap()

        /**
         * The request method that spawned this response.
         */
        var requestMethod: Method? = null

        /**
         * Use chunkedTransfer
         */
        private var chunkedTransfer = false

        /**
         * Default constructor: response = HTTP_OK, mime = MIME_HTML and your
         * supplied message
         */
        constructor(msg: String?) : this(Status.OK, MIME_HTML, msg) {}

        /**
         * Basic constructor.
         */
        constructor(status: IStatus?, mimeType: String?, data: InputStream?) {
            this.status = status
            this.mimeType = mimeType
            this.data = data
        }

        /**
         * Convenience method that makes an InputStream out of given text.
         */
        constructor(status: IStatus?, mimeType: String?, txt: String?) {
            this.status = status
            this.mimeType = mimeType
            try {
                data = if (txt != null) ByteArrayInputStream(
                        txt.toByteArray(charset("UTF-8"))) else null
            } catch (uee: UnsupportedEncodingException) {
                uee.printStackTrace()
            }
        }

        /**
         * Adds given line to the header.
         */
        fun addHeader(name: String, value: String?) {
            header!![name] = value
        }

        fun getHeader(name: String?): String? {
            return header!![name]
        }

        /**
         * Sends given response to the socket.
         */
        fun send(outputStream: OutputStream?) {
            val mime = mimeType
            val gmtFrmt = SimpleDateFormat(
                    "E, d MMM yyyy HH:mm:ss 'GMT'", Locale.US)
            gmtFrmt.timeZone = TimeZone.getTimeZone("GMT")
            try {
                if (status == null) {
                    throw Error("sendResponse(): Status can't be null.")
                }
                val pw = PrintWriter(outputStream)
                pw.print("""HTTP/1.1 ${status!!.description} 
""")
                if (mime != null) {
                    pw.print("Content-Type: $mime\r\n")
                }
                if (header == null || header["Date"] == null) {
                    pw.print("""
    Date: ${gmtFrmt.format(Date())}
    
    """.trimIndent())
                }
                if (header != null) {
                    for (key in header.keys) {
                        val value = header[key]
                        pw.print("$key: $value\r\n")
                    }
                }
                sendConnectionHeaderIfNotAlreadyPresent(pw, header)
                if (requestMethod != Method.HEAD && chunkedTransfer) {
                    sendAsChunked(outputStream, pw)
                } else {
                    val pending = if (data != null) data!!.available() else 0
                    sendContentLengthHeaderIfNotAlreadyPresent(pw, header,
                            pending)
                    pw.print("\r\n")
                    pw.flush()
                    sendAsFixedLength(outputStream, pending)
                }
                outputStream!!.flush()
                safeClose(data)
            } catch (ioe: IOException) {
                // Couldn't write? No can do.
            }
        }

        protected fun sendContentLengthHeaderIfNotAlreadyPresent(
                pw: PrintWriter, header: Map<String, String?>?, size: Int) {
            if (!headerAlreadySent(header, "content-length")) {
                pw.print("Content-Length: $size\r\n")
            }
        }

        protected fun sendConnectionHeaderIfNotAlreadyPresent(pw: PrintWriter,
                                                              header: Map<String, String?>?) {
            if (!headerAlreadySent(header, "connection")) {
                pw.print("Connection: keep-alive\r\n")
            }
        }

        private fun headerAlreadySent(header: Map<String, String?>?,
                                      name: String): Boolean {
            var alreadySent = false
            for (headerName in header!!.keys) {
                alreadySent = alreadySent or headerName.equals(name, ignoreCase = true)
            }
            return alreadySent
        }

        @Throws(IOException::class)
        private fun sendAsChunked(outputStream: OutputStream?, pw: PrintWriter) {
            pw.print("Transfer-Encoding: chunked\r\n")
            pw.print("\r\n")
            pw.flush()
            val BUFFER_SIZE = 16 * 1024
            val CRLF = "\r\n".toByteArray()
            val buff = ByteArray(BUFFER_SIZE)
            var read: Int
            while (data!!.read(buff).also { read = it } > 0) {
                outputStream!!.write(String.format("%x\r\n", read).toByteArray())
                outputStream.write(buff, 0, read)
                outputStream.write(CRLF)
            }
            outputStream!!.write(String.format("0\r\n\r\n").toByteArray())
        }

        @Throws(IOException::class)
        private fun sendAsFixedLength(outputStream: OutputStream?, pending: Int) {
            var pending = pending
            if (requestMethod != Method.HEAD && data != null) {
                val BUFFER_SIZE = 16 * 1024
                val buff = ByteArray(BUFFER_SIZE)
                while (pending > 0) {
                    val read = data!!.read(buff, 0,
                            if (pending > BUFFER_SIZE) BUFFER_SIZE else pending)
                    if (read <= 0) {
                        break
                    }
                    outputStream!!.write(buff, 0, read)
                    pending -= read
                }
            }
        }

        fun setStatus(status: Status?) {
            this.status = status
        }

        fun setChunkedTransfer(chunkedTransfer: Boolean) {
            this.chunkedTransfer = chunkedTransfer
        }

        interface IStatus {
            val requestStatus: Int
            val description: String

        }

        /**
         * Some HTTP response status codes
         */
        enum class Status(override val requestStatus: Int, override val description: String) : IStatus {
            SWITCH_PROTOCOL(101, "Switching Protocols"), OK(200, "OK"), CREATED(
                    201, "Created"),
            ACCEPTED(202, "Accepted"), NO_CONTENT(204,
                    "No Content"),
            PARTIAL_CONTENT(206, "Partial Content"), REDIRECT(
                    301, "Moved Permanently"),
            NOT_MODIFIED(304, "Not Modified"), BAD_REQUEST(
                    400, "Bad Request"),
            UNAUTHORIZED(401, "Unauthorized"), FORBIDDEN(
                    403, "Forbidden"),
            NOT_FOUND(404, "Not Found"), METHOD_NOT_ALLOWED(
                    405, "Method Not Allowed"),
            RANGE_NOT_SATISFIABLE(416,
                    "Requested Range Not Satisfiable"),
            INTERNAL_ERROR(500,
                    "Internal Server Error");

            //fun getDescription(): String {
            //    return "" + requestStatus + " " + description
            //}

        }
    }

    class ResponseException : Exception {
        val status: Response.Status

        constructor(status: Response.Status, message: String?) : super(message) {
            this.status = status
        }

        constructor(status: Response.Status, message: String?,
                    e: Exception?) : super(message, e) {
            this.status = status
        }

        companion object {
            /**
             *
             */
            private const val serialVersionUID = 6569838532917408380L
        }
    }

    /**
     * Default strategy for creating and cleaning up temporary files.
     */
    private inner class DefaultTempFileManagerFactory : TempFileManagerFactory {
        override fun create(): TempFileManager {
            return DefaultTempFileManager()
        }
    }

    /**
     * Handles one session, i.e. parses the HTTP request and returns the
     * response.
     */
    interface IHTTPSession {
        @Throws(IOException::class)
        fun execute()
        val parms: MutableMap<String, String?>?
        val headers: Map<String?, String?>?

        /**
         * @return the path part of the URL.
         */
        val uri: String?
        val queryParameterString: String?
        val method: Method?
        val inputStream: InputStream
        val cookies: CookieHandler?

        /**
         * Adds the files in the request body to the files map.
         *
         * @arg files - map to modify
         */
        @Throws(IOException::class, ResponseException::class)
        fun parseBody(files: MutableMap<String, String>)
    }

    protected inner class HTTPSession : IHTTPSession {
        private val tempFileManager: TempFileManager
        private val outputStream: OutputStream?
        override var inputStream: PushbackInputStream
        private var splitbyte = 0
        private var rlen = 0
        override var uri: String? = null
            private set
        override var method: Method? = null
            private set
        override var parms: MutableMap<String, String?>? = null
            private set
        override var headers: MutableMap<String?, String?>? = null
        override var cookies: CookieHandler? = null
            private set
        override var queryParameterString: String? = null
            private set

        constructor(tempFileManager: TempFileManager,
                    inputStream: InputStream?, outputStream: OutputStream?) {
            this.tempFileManager = tempFileManager
            this.inputStream = PushbackInputStream(inputStream, BUFSIZE)
            this.outputStream = outputStream
        }

        constructor(tempFileManager: TempFileManager,
                    inputStream: InputStream?, outputStream: OutputStream?,
                    inetAddress: InetAddress) {
            this.tempFileManager = tempFileManager
            this.inputStream = PushbackInputStream(inputStream, BUFSIZE)
            this.outputStream = outputStream
            val remoteIp = if (inetAddress.isLoopbackAddress
                    || inetAddress.isAnyLocalAddress) "127.0.0.1" else inetAddress.hostAddress.toString()
            headers = HashMap()
            (headers as HashMap<String?, String?>)["remote-addr"] = remoteIp
            (headers as HashMap<String?, String?>)["http-client-ip"] = remoteIp
        }

        @Throws(IOException::class)
        override fun execute() {
            try {
                // Read the first 8192 bytes.
                // The full header should fit in here.
                // Apache's default header limit is 8KB.
                // Do NOT assume that a single read will get the entire header
                // at once!
                val buf = ByteArray(BUFSIZE)
                splitbyte = 0
                rlen = 0
                run {
                    var read = -1
                    read = try {
                        inputStream.read(buf, 0, BUFSIZE)
                    } catch (e: Exception) {
                        safeClose(inputStream)
                        safeClose(outputStream)
                        throw SocketException("NanoHttpd Shutdown")
                    }
                    if (read == -1) {
                        // socket was been closed
                        safeClose(inputStream)
                        safeClose(outputStream)
                        throw SocketException("NanoHttpd Shutdown")
                    }
                    while (read > 0) {
                        rlen += read
                        splitbyte = findHeaderEnd(buf, rlen)
                        if (splitbyte > 0) break
                        read = inputStream.read(buf, rlen, BUFSIZE - rlen)
                    }
                }
                if (splitbyte < rlen) {
                    inputStream.unread(buf, splitbyte, rlen - splitbyte)
                }
                parms = HashMap()
                if (null == headers) {
                    headers = HashMap()
                }

                // Create a BufferedReader for parsing the header.
                val hin = BufferedReader(InputStreamReader(
                        ByteArrayInputStream(buf, 0, rlen)))

                // Decode the header into parms and header java properties
                val pre: MutableMap<String, String?> = HashMap()
                decodeHeader(hin, pre, parms as HashMap<String, String?>, headers!!)
                method = Method.lookup(pre["method"])
                if (method == null) {
                    throw ResponseException(Response.Status.BAD_REQUEST,
                            "BAD REQUEST: Syntax error.")
                }
                uri = pre["uri"]
                cookies = CookieHandler(headers!!)

                // Ok, now do the serve()
                val r = serve(this)
                if (r == null) {
                    throw ResponseException(Response.Status.INTERNAL_ERROR,
                            "SERVER INTERNAL ERROR: Serve() returned a null response.")
                } else {
                    cookies!!.unloadQueue(r)
                    r.requestMethod = method
                    r.send(outputStream)
                }
            } catch (e: SocketException) {
                // throw it out to close socket object (finalAccept)
                throw e
            } catch (ste: SocketTimeoutException) {
                throw ste
            } catch (ioe: IOException) {
                val r = Response(Response.Status.INTERNAL_ERROR,
                        MIME_PLAINTEXT, "SERVER INTERNAL ERROR: IOException: "
                        + ioe.message)
                r.send(outputStream)
                safeClose(outputStream)
            } catch (re: ResponseException) {
                val r = Response(re.status, MIME_PLAINTEXT,
                        re.message)
                r.send(outputStream)
                safeClose(outputStream)
            } finally {
                tempFileManager.clear()
            }
        }

        @Throws(IOException::class, ResponseException::class)
        override fun parseBody(files: MutableMap<String, String>) {
            var randomAccessFile: RandomAccessFile? = null
            var `in`: BufferedReader? = null
            try {
                randomAccessFile = tmpBucket
                var size: Long
                size = if (headers!!.containsKey("content-length")) {
                    headers!!["content-length"]!!.toInt().toLong()
                } else if (splitbyte < rlen) {
                    rlen - splitbyte.toLong()
                } else {
                    0
                }

                // Now read all the body and write it to f
                val buf = ByteArray(512)
                while (rlen >= 0 && size > 0) {
                    rlen = inputStream.read(buf, 0, Math.min(size, 512).toInt())
                    size -= rlen.toLong()
                    if (rlen > 0) {
                        randomAccessFile.write(buf, 0, rlen)
                    }
                }

                // Get the raw body as a byte []
                val fbuf: ByteBuffer = randomAccessFile.channel.map(
                        FileChannel.MapMode.READ_ONLY, 0,
                        randomAccessFile.length())
                randomAccessFile.seek(0)

                // Create a BufferedReader for easily reading it as string.
                val bin: InputStream = FileInputStream(randomAccessFile.fd)
                `in` = BufferedReader(InputStreamReader(bin))

                // If the method is POST, there may be parameters
                // in data section, too, read it:
                if (Method.POST == method) {
                    var contentType = ""
                    val contentTypeHeader = headers!!["content-type"]
                    var st: StringTokenizer? = null
                    if (contentTypeHeader != null) {
                        st = StringTokenizer(contentTypeHeader, ",; ")
                        if (st.hasMoreTokens()) {
                            contentType = st.nextToken()
                        }
                    }
                    if ("multipart/form-data".equals(contentType, ignoreCase = true)) {
                        // Handle multipart/form-data
                        if (!st!!.hasMoreTokens()) {
                            throw ResponseException(
                                    Response.Status.BAD_REQUEST,
                                    "BAD REQUEST: Content type is multipart/form-data but boundary missing. Usage: GET /example/file.html")
                        }
                        val boundaryStartString = "boundary="
                        val boundaryContentStart = (contentTypeHeader!!
                                .indexOf(boundaryStartString)
                                + boundaryStartString.length)
                        var boundary = contentTypeHeader!!.substring(
                                boundaryContentStart,
                                contentTypeHeader.length)
                        if (boundary.startsWith("\"")
                                && boundary.endsWith("\"")) {
                            boundary = boundary.substring(1,
                                    boundary.length - 1)
                        }
                        decodeMultipartData(boundary, fbuf, `in`, parms, files)
                    } else {
                        var postLine = ""
                        val postLineBuffer = StringBuilder()
                        val pbuf = CharArray(512)
                        var read = `in`.read(pbuf)
                        while (read >= 0 && !postLine.endsWith("\r\n")) {
                            postLine = String(pbuf, 0, read)
                            postLineBuffer.append(postLine)
                            read = `in`.read(pbuf)
                        }
                        postLine = postLineBuffer.toString().trim { it <= ' ' }
                        // Handle application/x-www-form-urlencoded
                        if ("application/x-www-form-urlencoded"
                                        .equals(contentType, ignoreCase = true)) {
                            decodeParms(postLine, parms)
                        } else if (postLine.length != 0) {
                            // Special case for raw POST data => create a
                            // special files entry "postData" with raw content
                            // data
                            files["postData"] = postLine
                        }
                    }
                } else if (Method.PUT == method) {
                    files["content"] = saveTmpFile(fbuf, 0, fbuf.limit())
                }
            } finally {
                safeClose(randomAccessFile)
                safeClose(`in`)
            }
        }

        /**
         * Decodes the sent headers and loads the data into Key/value pairs
         */
        @Throws(ResponseException::class)
        private fun decodeHeader(`in`: BufferedReader, pre: MutableMap<String, String?>,
                                 parms: MutableMap<String, String?>, headers: MutableMap<String?, String?>) {
            try {
                // Read the request line
                val inLine = `in`.readLine() ?: return
                val st = StringTokenizer(inLine)
                if (!st.hasMoreTokens()) {
                    throw ResponseException(Response.Status.BAD_REQUEST,
                            "BAD REQUEST: Syntax error. Usage: GET /example/file.html")
                }
                pre["method"] = st.nextToken()
                if (!st.hasMoreTokens()) {
                    throw ResponseException(Response.Status.BAD_REQUEST,
                            "BAD REQUEST: Missing URI. Usage: GET /example/file.html")
                }
                var uri = st.nextToken()

                // Decode parameters from the URI
                val qmi = uri!!.indexOf('?')
                uri = if (qmi >= 0) {
                    decodeParms(uri.substring(qmi + 1), parms)
                    decodePercent(uri.substring(0, qmi))
                } else {
                    decodePercent(uri)
                }

                // If there's another token, it's protocol version,
                // followed by HTTP headers. Ignore version but parse headers.
                // NOTE: this now forces header names lowercase since they are
                // case insensitive and vary by client.
                if (st.hasMoreTokens()) {
                    var line = `in`.readLine()
                    while (line != null && line.trim { it <= ' ' }.length > 0) {
                        val p = line.indexOf(':')
                        if (p >= 0) headers[line.substring(0, p).trim { it <= ' ' }
                                .toLowerCase(Locale.US)] = line.substring(p + 1).trim { it <= ' ' }
                        line = `in`.readLine()
                    }
                }
                pre["uri"] = uri
            } catch (ioe: IOException) {
                throw ResponseException(Response.Status.INTERNAL_ERROR, "SERVER INTERNAL ERROR: IOException: "
                        + ioe.message, ioe)
            }
        }

        /**
         * Decodes the Multipart Body data and put it into Key/Value pairs.
         */
        @Throws(ResponseException::class)
        private fun decodeMultipartData(boundary: String, fbuf: ByteBuffer,
                                        `in`: BufferedReader, parms: MutableMap<String, String?>?,
                                        files: MutableMap<String, String>) {
            try {
                val bpositions = getBoundaryPositions(fbuf,
                        boundary.toByteArray())
                var boundarycount = 1
                var mpline = `in`.readLine()
                while (mpline != null) {
                    if (!mpline.contains(boundary)) {
                        throw ResponseException(
                                Response.Status.BAD_REQUEST,
                                "BAD REQUEST: Content type is multipart/form-data but next chunk does not start with boundary. Usage: GET /example/file.html")
                    }
                    boundarycount++
                    val item: MutableMap<String, String> = HashMap()
                    mpline = `in`.readLine()
                    while (mpline != null && mpline.trim { it <= ' ' }.length > 0) {
                        val p = mpline.indexOf(':')
                        if (p != -1) {
                            item[mpline.substring(0, p).trim { it <= ' ' }
                                    .toLowerCase(Locale.US)] = mpline
                                    .substring(p + 1).trim { it <= ' ' }
                        }
                        mpline = `in`.readLine()
                    }
                    if (mpline != null) {
                        val contentDisposition = item["content-disposition"]
                                ?: throw ResponseException(
                                        Response.Status.BAD_REQUEST,
                                        "BAD REQUEST: Content type is multipart/form-data but no content-disposition info found. Usage: GET /example/file.html")
                        val st = StringTokenizer(
                                contentDisposition, ";")
                        val disposition: MutableMap<String, String> = HashMap()
                        while (st.hasMoreTokens()) {
                            val token = st.nextToken().trim { it <= ' ' }
                            val p = token.indexOf('=')
                            if (p != -1) {
                                disposition[token.substring(0, p).trim { it <= ' ' }
                                        .toLowerCase(Locale.US)] = token
                                        .substring(p + 1).trim { it <= ' ' }
                            }
                        }
                        var pname = disposition["name"]
                        pname = pname!!.substring(1, pname.length - 1)
                        var value: String? = ""
                        if (item["content-type"] == null) {
                            while (mpline != null && !mpline.contains(boundary)) {
                                mpline = `in`.readLine()
                                if (mpline != null) {
                                    val d = mpline.indexOf(boundary)
                                    value += if (d == -1) {
                                        mpline
                                    } else {
                                        mpline.substring(0, d - 2)
                                    }
                                }
                            }
                        } else {
                            if (boundarycount > bpositions.size) {
                                throw ResponseException(
                                        Response.Status.INTERNAL_ERROR,
                                        "Error processing request")
                            }
                            val offset = stripMultipartHeaders(fbuf,
                                    bpositions[boundarycount - 2])
                            val path = saveTmpFile(fbuf, offset,
                                    bpositions[boundarycount - 1] - offset - 4)
                            files[pname] = path
                            value = disposition["filename"]
                            value = value!!.substring(1, value.length - 1)
                            do {
                                mpline = `in`.readLine()
                            } while (mpline != null
                                    && !mpline.contains(boundary))
                        }
                        parms!![pname] = value
                    }
                }
            } catch (ioe: IOException) {
                throw ResponseException(Response.Status.INTERNAL_ERROR, "SERVER INTERNAL ERROR: IOException: "
                        + ioe.message, ioe)
            }
        }

        /**
         * Find byte index separating header from body. It must be the last byte
         * of the first two sequential new lines.
         */
        private fun findHeaderEnd(buf: ByteArray, rlen: Int): Int {
            var splitbyte = 0
            while (splitbyte + 3 < rlen) {
                if (buf[splitbyte] == '\r'.toByte() && buf[splitbyte + 1] == '\n'.toByte() && buf[splitbyte + 2] == '\r'.toByte() && buf[splitbyte + 3] == '\n'.toByte()) {
                    return splitbyte + 4
                }
                splitbyte++
            }
            return 0
        }

        /**
         * Find the byte positions where multipart boundaries start.
         */
        private fun getBoundaryPositions(b: ByteBuffer, boundary: ByteArray): IntArray {
            var matchcount = 0
            var matchbyte = -1
            val matchbytes: MutableList<Int> = ArrayList()
            run {
                var i = 0
                while (i < b.limit()) {
                    if (b[i] == boundary[matchcount]) {
                        if (matchcount == 0) matchbyte = i
                        matchcount++
                        if (matchcount == boundary.size) {
                            matchbytes.add(matchbyte)
                            matchcount = 0
                            matchbyte = -1
                        }
                    } else {
                        i -= matchcount
                        matchcount = 0
                        matchbyte = -1
                    }
                    i++
                }
            }
            val ret = IntArray(matchbytes.size)
            for (i in ret.indices) {
                ret[i] = matchbytes[i]
            }
            return ret
        }

        /**
         * Retrieves the content of a sent file and saves it to a temporary
         * file. The full path to the saved file is returned.
         */
        private fun saveTmpFile(b: ByteBuffer, offset: Int, len: Int): String {
            var path = ""
            if (len > 0) {
                var fileOutputStream: FileOutputStream? = null
                try {
                    val tempFile = tempFileManager.createTempFile()
                    val src = b.duplicate()
                    fileOutputStream = FileOutputStream(tempFile.name)
                    val dest = fileOutputStream.channel
                    src.position(offset).limit(offset + len)
                    dest.write(src.slice())
                    path = tempFile.name
                } catch (e: Exception) { // Catch exception if any
                    throw Error(e) // we won't recover, so throw an error
                } finally {
                    safeClose(fileOutputStream)
                }
            }
            return path
        }

        // we won't recover, so throw an error
        private val tmpBucket: RandomAccessFile
            private get() = try {
                val tempFile = tempFileManager.createTempFile()
                RandomAccessFile(tempFile.name, "rw")
            } catch (e: Exception) {
                throw Error(e) // we won't recover, so throw an error
            }

        /**
         * It returns the offset separating multipart file headers from the
         * file's data.
         */
        private fun stripMultipartHeaders(b: ByteBuffer, offset: Int): Int {
            var i: Int
            i = offset
            while (i < b.limit()) {
                if (b[i] == '\r'.toByte() && b[++i] == '\n'.toByte() && b[++i] == '\r'.toByte() && b[++i] == '\n'.toByte()) {
                    break
                }
                i++
            }
            return i + 1
        }

        /**
         * Decodes parameters in percent-encoded URI-format ( e.g.
         * "name=Jack%20Daniels&pass=Single%20Malt" ) and adds them to given
         * Map. NOTE: this doesn't support multiple identical keys due to the
         * simplicity of Map.
         */
        private fun decodeParms(parms: String?, p: MutableMap<String, String?>?) {
            if (parms == null) {
                queryParameterString = ""
                return
            }
            queryParameterString = parms
            val st = StringTokenizer(parms, "&")
            while (st.hasMoreTokens()) {
                val e = st.nextToken()
                val sep = e.indexOf('=')
                if (sep >= 0) {
                    p!![decodePercent(e.substring(0, sep))!!.trim { it <= ' ' }] = decodePercent(e.substring(sep + 1))
                } else {
                    p!![decodePercent(e)!!.trim { it <= ' ' }] = ""
                }
            }
        }

        //override fun getHeaders(): Map<String?, String?>? {
        //    return headers
        //}

        //override fun getInputStream(): InputStream {
        //    return inputStream
        //}

        //companion object {
        //    const val BUFSIZE = 8192
        //}
        val BUFSIZE = 8192
    }

    class Cookie {
        private var n: String
        private var v: String
        private var e: String

        constructor(name: String, value: String, expires: String) {
            n = name
            v = value
            e = expires
        }

        @JvmOverloads
        constructor(name: String, value: String, numDays: Int = 30) {
            n = name
            v = value
            e = getHTTPTime(numDays)
        }

        val hTTPHeader: String
            get() {
                val fmt = "%s=%s; expires=%s"
                return String.format(fmt, n, v, e)
            }

        companion object {
            fun getHTTPTime(days: Int): String {
                val calendar = Calendar.getInstance()
                val dateFormat = SimpleDateFormat(
                        "EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
                dateFormat.timeZone = TimeZone.getTimeZone("GMT")
                calendar.add(Calendar.DAY_OF_MONTH, days)
                return dateFormat.format(calendar.time)
            }
        }
    }

    /**
     * Provides rudimentary support for cookies. Doesn't support 'path',
     * 'secure' nor 'httpOnly'. Feel free to improve it and/or add unsupported
     * features.
     *
     * @author LordFokas
     */
    inner class CookieHandler(httpHeaders: Map<String?, String?>) : Iterable<String?> {
        private val cookies = HashMap<String, String>()
        private val queue = ArrayList<Cookie>()
        override fun iterator(): MutableIterator<String> {
            return cookies.keys.iterator()
        }

        /**
         * Read a cookie from the HTTP Headers.
         *
         * @param name
         * The cookie's name.
         * @return The cookie's value if it exists, null otherwise.
         */
        fun read(name: String?): String? {
            return cookies[name]
        }

        /**
         * Sets a cookie.
         *
         * @param name
         * The cookie's name.
         * @param value
         * The cookie's value.
         * @param expires
         * How many days until the cookie expires.
         */
        operator fun set(name: String, value: String, expires: Int) {
            queue.add(Cookie(name, value, Cookie.getHTTPTime(expires)))
        }

        fun set(cookie: Cookie) {
            queue.add(cookie)
        }

        /**
         * Set a cookie with an expiration date from a month ago, effectively
         * deleting it on the client side.
         *
         * @param name
         * The cookie name.
         */
        fun delete(name: String) {
            set(name, "-delete-", -30)
        }

        /**
         * Internally used by the webserver to add all queued cookies into the
         * Response's HTTP Headers.
         *
         * @param response
         * The Response object to which headers the queued cookies
         * will be added.
         */
        fun unloadQueue(response: Response) {
            for (cookie in queue) {
                response.addHeader("Set-Cookie", cookie.hTTPHeader)
            }
        }

        init {
            val raw = httpHeaders["cookie"]
            if (raw != null) {
                val tokens = raw.split(";").toTypedArray()
                for (token in tokens) {
                    val data = token.trim { it <= ' ' }.split("=").toTypedArray()
                    if (data.size == 2) {
                        cookies[data[0]] = data[1]
                    }
                }
            }
        }
    }

    companion object {
        /**
         * Maximum time to wait on Socket.getInputStream().read() (in milliseconds)
         * This is required as the Keep-Alive HTTP connections would otherwise block
         * the socket reading thread forever (or as long the browser is open).
         */
        const val SOCKET_READ_TIMEOUT = 5000

        /**
         * Common mime type for dynamic content: plain text
         */
        const val MIME_PLAINTEXT = "text/plain"

        /**
         * Common mime type for dynamic content: html
         */
        const val MIME_HTML = "text/html"

        /**
         * Pseudo-Parameter to use to store the actual query string in the
         * parameters map for later re-processing.
         */
        private const val QUERY_STRING_PARAMETER = "NanoHttpd.QUERY_STRING"
        private fun safeClose(closeable: Closeable?) {
            if (closeable != null) {
                try {
                    closeable.close()
                } catch (e: IOException) {
                }
            }
        }

        private fun safeClose(closeable: Socket?) {
            if (closeable != null) {
                try {
                    closeable.close()
                } catch (e: IOException) {
                }
            }
        }

        private fun safeClose(closeable: ServerSocket?) {
            if (closeable != null) {
                try {
                    closeable.close()
                } catch (e: IOException) {
                }
            }
        }
    }

    /**
     * Constructs an HTTP server on given hostname and port.
     */
    init {
        setTempFileManagerFactory(DefaultTempFileManagerFactory())
        setAsyncRunner(DefaultAsyncRunner())
    }
}