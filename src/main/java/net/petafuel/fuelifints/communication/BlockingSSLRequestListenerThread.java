package net.petafuel.fuelifints.communication;

import net.petafuel.fuelifints.FinTSServer;
import net.petafuel.fuelifints.FinTSVersionSwitch;
import org.apache.http.ConnectionClosedException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.DefaultHttpServerConnection;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.params.SyncBasicHttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.HttpRequestHandlerRegistry;
import org.apache.http.protocol.HttpService;
import org.apache.http.protocol.ImmutableHttpProcessor;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.util.encoders.Base64;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.Socket;
import java.util.Properties;

public class BlockingSSLRequestListenerThread extends Thread {

    private static final Logger LOG = LogManager.getLogger(BlockingSSLRequestListenerThread.class);
    private boolean shouldStop = false;


    private final SSLServerSocket serversocket;
    private final HttpParams params;
    private final HttpService httpService;

    private FinTSServer finTSServer;

    public BlockingSSLRequestListenerThread(int port, FinTSServer server) throws IOException {
        this.finTSServer = server;
        Properties systemProps = System.getProperties();
        Properties properties = new Properties();
        String[] useCipherSuites = null;
        String[] useProtocols = null;
        try {
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream("config/fuelifints.properties"));
            properties.load(bis);
            bis.close();
            systemProps.put("javax.net.ssl.keyStore", properties.getProperty("keystore_location"));
            systemProps.put("javax.net.ssl.keyStorePassword", properties.getProperty("keystore_password"));
            useCipherSuites = properties.getProperty("use_cipher_suites").split(";");
            useProtocols = properties.getProperty("use_protocols").split(";");
        } catch (Exception e) {
            LOG.error("I/O error in constructor", e.getMessage());
        }
        System.setProperties(systemProps);

        SSLServerSocketFactory sslsocketfactory = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();

        serversocket = (SSLServerSocket) sslsocketfactory.createServerSocket(port, 0, finTSServer.getServerAdress());
        if (useCipherSuites != null) {
            serversocket.setEnabledCipherSuites(useCipherSuites);
        }
        if (useProtocols != null) {
            serversocket.setEnabledProtocols(useProtocols);
        }
        this.params = new SyncBasicHttpParams();
        this.params.setIntParameter(CoreConnectionPNames.SO_TIMEOUT, 5000)
                .setIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, 8 * 1024)
                .setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK, false)
                .setBooleanParameter(CoreConnectionPNames.TCP_NODELAY, true)
                .setParameter(CoreProtocolPNames.ORIGIN_SERVER, "HttpComponents/1.1");

        // Set up the HTTP protocol processor
        HttpProcessor httpproc = new ImmutableHttpProcessor(new HttpResponseInterceptor[]{
                new ResponseDate(),
                new ResponseServer(),
                new ResponseContent(),
                new ResponseConnControl()
        });

        // Set up request handlers
        HttpRequestHandlerRegistry reqistry = new HttpRequestHandlerRegistry();
        reqistry.register("*", new SslRequestHandler());

        // Set up the HTTP service
        this.httpService = new HttpService(
                httpproc,
                new DefaultConnectionReuseStrategy(),
                new DefaultHttpResponseFactory(),
                reqistry,
                this.params);
    }

    public void stopListening() {
        try {
            shouldStop = true;
            serversocket.close();
        } catch (IOException e) {
            LOG.error("I/O error in stopListening: ", e.getMessage());
        }
    }

    @Override
    public void run() {
        LOG.info("SSLServer listening on port {}", this.serversocket.getLocalPort());
        shouldStop = false;
        while (!shouldStop) {
            try {
                // Set up HTTP connection
                LOG.info("Accepting new incoming connection");
                final Socket socket = this.serversocket.accept();

                new Thread() {
                    public void run() {
                        DefaultHttpServerConnection conn = new DefaultHttpServerConnection();
                        LOG.info("Incoming connection from {}", socket.getInetAddress());
                        try {
                            conn.bind(socket, BlockingSSLRequestListenerThread.this.params);
                        } catch (IOException e) {
                            LOG.error("io exception ", e);
                        }

                        HttpContext context = new BasicHttpContext(null);
                        while (conn.isOpen()) {
                            try {
                                BlockingSSLRequestListenerThread.this.httpService.handleRequest(conn, context);
                            } catch (ConnectionClosedException ex) {
                                LOG.debug("Client closed connection");
                                try {
                                    conn.shutdown();
                                } catch (IOException e) {

                                }
                            } catch (IOException ex) {
                                LOG.error("I/O error", ex.getMessage());
                                LOG.debug("I/O error", ex.getStackTrace());
                                try {
                                    conn.shutdown();
                                } catch (IOException e) {

                                }
                            } catch (HttpException ex) {
                                LOG.debug("Unrecoverable HTTP protocol violation", ex);
                                try {
                                    conn.shutdown();
                                } catch (IOException e) {

                                }
                            } /*finally {
                                try {
                                    conn.shutdown();
                                } catch (IOException ignore) {
                                }
                            }   */
                        }
                    }
                }.start();
            } catch (InterruptedIOException e) {
                LOG.error("Blocking SSL Listener interupted", e.getMessage());
                break;
            } catch (IOException e) {
                LOG.error("I/O error initializing connection thread", e.getMessage());
                LOG.debug("I/O error initializing connection thread", e.getStackTrace());
                break;
            }
        }
    }

    class SslRequestHandler implements HttpRequestHandler {


        @Override
        public void handle(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException, IOException {

            String method = request.getRequestLine().getMethod().toUpperCase();
            if (!method.equals("POST")) {
                response.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
                response.removeHeaders("Server");
                response.setHeader(new BasicHeader("Server", ""));
                return;

                //throw new MethodNotSupportedException(method + " method not supported");
            }

            final HttpResponse httpResponse = response;

            FinTSCommunicationHandler handler = new FinTSCommunicationHandler(FinTSCommunicationHandler.CommunicationChannel.SSL) {
                @Override
                public void sendResponse(byte[] responseData, boolean isLastMessage) {
                    updateTimeStamp();
                    httpResponse.setStatusCode(HttpStatus.SC_OK);
                    ByteArrayEntity entity = new ByteArrayEntity(Base64.encode(responseData));
                    httpResponse.setEntity(entity);
                    synchronized (httpResponse) {
                        httpResponse.notifyAll();
                    }
                }
            };

            finTSServer.addRequest(handler);

            if (request instanceof HttpEntityEnclosingRequest) {
                HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
                byte[] entityContent = EntityUtils.toByteArray(entity);
                byte[] decoded = Base64.decode(new String(entityContent));
                if (FinTSVersionSwitch.selectController(handler.getTaskId(), decoded, FinTSCommunicationHandler.CommunicationChannel.SSL)) {
                    synchronized (httpResponse) {
                        try {
                            httpResponse.wait();
                        } catch (InterruptedException ex) {
                            LOG.error(ex.getMessage());
                        }
                    }
                }
            }

        }
    }

}
