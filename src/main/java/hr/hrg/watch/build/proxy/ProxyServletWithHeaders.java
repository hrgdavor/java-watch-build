package hr.hrg.watch.build.proxy;

import java.io.*;
import java.nio.*;
import java.util.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.eclipse.jetty.client.*;
import org.eclipse.jetty.client.api.*;
import org.eclipse.jetty.client.api.Response.*;
import org.eclipse.jetty.http.*;
import org.eclipse.jetty.proxy.*;
import org.eclipse.jetty.util.*;
import org.eclipse.jetty.util.ssl.*;

public class ProxyServletWithHeaders extends ProxyServlet.Transparent{
	volatile boolean first = true;
	
	@Override
	protected HttpClient newHttpClient() {
        SslContextFactory scf = new SslContextFactory();
        scf.setTrustAll(true);

        return new HttpClient(scf);
	}
	
	@Override
	protected void addProxyHeaders(HttpServletRequest clientRequest, Request proxyRequest) {
		super.addProxyHeaders(clientRequest, proxyRequest);

		if(first) {
			first = false;
			System.err.println("Proxy from "+clientRequest.getRequestURI()+" -> "+proxyRequest.getURI());
		}		
	}
	
	@Override
	protected Listener newProxyResponseListener(HttpServletRequest request, HttpServletResponse response) {
		return new ProxyResponseListener(request, response);
	}

	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		super.service(request, response);
	}
		
	@Override
	protected void onProxyResponseSuccess(HttpServletRequest clientRequest, HttpServletResponse proxyResponse, Response serverResponse) {
		super.onProxyResponseSuccess(clientRequest, proxyResponse, serverResponse);
	}
	
	@Override
	protected void onServerResponseHeaders(HttpServletRequest clientRequest, HttpServletResponse proxyResponse, Response serverResponse) {
		super.onServerResponseHeaders(clientRequest, proxyResponse, serverResponse);

        HttpFields headerNames = serverResponse.getHeaders();
        for (HttpField header : headerNames) {
        	if("Set-Cookie".equals(header.getName())) {
        		String cookieStr = header.getValue();
        		int idx = cookieStr.indexOf(";");
        		if(idx != -1) {
        			cookieStr = cookieStr.substring(0,idx);
        			idx = cookieStr.indexOf("=");
        			if(idx != -1) {
        				proxyResponse.addCookie(new Cookie(cookieStr.substring(0,idx), cookieStr.substring(idx+1)));
        			}
        		}
        		proxyResponse.addHeader(header.getName(), header.getValue());
        		proxyResponse.addHeader("X-"+header.getName(), header.getValue());
        	}
        }

	}
	
    protected class ProxyResponseListener extends Response.Listener.Adapter
    {
        private final HttpServletRequest request;
        private final HttpServletResponse response;

        protected ProxyResponseListener(HttpServletRequest request, HttpServletResponse response)
        {
            this.request = request;
            this.response = response;
        }

        @Override
        public void onBegin(Response proxyResponse)
        {
            response.setStatus(proxyResponse.getStatus());
        }

        @Override
        public void onSuccess(Response response) {
        	super.onSuccess(response);
        }
        
        @Override
        public void onHeaders(Response proxyResponse)
        {
            onServerResponseHeaders(request, response, proxyResponse);
        }
        

        @Override
        public void onContent(final Response proxyResponse, ByteBuffer content, final Callback callback)
        {
            byte[] buffer;
            int offset;
            int length = content.remaining();
            if (content.hasArray())
            {
                buffer = content.array();
                offset = content.arrayOffset();
            }
            else
            {
                buffer = new byte[length];
                content.get(buffer);
                offset = 0;
            }

            onResponseContent(request, response, proxyResponse, buffer, offset, length, new Callback.Nested(callback)
            {
                @Override
                public void failed(Throwable x)
                {
                    super.failed(x);
                    proxyResponse.abort(x);
                }
            });
        }

        @Override
        public void onComplete(Result result)
        {
            if (result.isSucceeded())
                onProxyResponseSuccess(request, response, result.getResponse());
            else
                onProxyResponseFailure(request, response, result.getResponse(), result.getFailure());
            if (_log.isDebugEnabled())
                _log.debug("{} proxying complete", getRequestId(request));
        }
    }
	
}