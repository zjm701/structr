/**
 * Copyright (C) 2010-2016 Structr GmbH
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.web.servlet;

import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.structr.common.ThreadLocalMatcher;
import org.structr.core.Services;
import org.structr.core.app.StructrApp;
import org.structr.core.auth.Authenticator;
import org.structr.core.entity.AbstractNode;
import org.structr.rest.service.HttpService;
import org.structr.rest.service.HttpServiceServlet;
import org.structr.rest.service.StructrHttpServiceConfig;
import org.structr.web.auth.UiAuthenticator;
import org.structr.web.entity.dom.Page;

//~--- classes ----------------------------------------------------------------
/**
 * Servlet for proxy requests.
 *
 *
 *
 */
public class ProxyServlet extends HttpServlet implements HttpServiceServlet {

	private static final Logger logger = Logger.getLogger(ProxyServlet.class.getName());

	public static final String CONFIRM_REGISTRATION_PAGE = "/confirm_registration";
	public static final String RESET_PASSWORD_PAGE       = "/reset-password";
	public static final String POSSIBLE_ENTRY_POINTS_KEY = "possibleEntryPoints";
	public static final String DOWNLOAD_AS_FILENAME_KEY  = "filename";
	public static final String RANGE_KEY                 = "range";
	public static final String DOWNLOAD_AS_DATA_URL_KEY  = "as-data-url";
	public static final String CONFIRM_KEY_KEY           = "key";
	public static final String TARGET_PAGE_KEY           = "target";
	public static final String ERROR_PAGE_KEY            = "onerror";

	public static final String CUSTOM_RESPONSE_HEADERS      = "HtmlServlet.customResponseHeaders";
	public static final String OBJECT_RESOLUTION_PROPERTIES = "HtmlServlet.resolveProperties";

	private static final String defaultCustomResponseHeaders = "Strict-Transport-Security:max-age=60,"
				+ "X-Content-Type-Options:nosniff,"
				+ "X-Frame-Options:SAMEORIGIN,"
				+ "X-XSS-Protection:1;mode=block";
	private static List<String> customResponseHeaders = Collections.EMPTY_LIST;

	private static final ThreadLocalMatcher threadLocalUUIDMatcher = new ThreadLocalMatcher("[a-fA-F0-9]{32}");
	private static final ExecutorService threadPool = Executors.newCachedThreadPool();

	private final StructrHttpServiceConfig config = new StructrHttpServiceConfig();
	private final Set<String> possiblePropertyNamesForEntityResolving   = new LinkedHashSet<>();

	private boolean isAsync = false;


	@Override
	public StructrHttpServiceConfig getConfig() {
		return config;
	}

	public ProxyServlet() {

		String customResponseHeadersString = Services.getBaseConfiguration().getProperty(CUSTOM_RESPONSE_HEADERS);

		if (StringUtils.isBlank(customResponseHeadersString)) {

			customResponseHeadersString = defaultCustomResponseHeaders;
		}

		if (StringUtils.isNotBlank(customResponseHeadersString)) {
			customResponseHeaders = Arrays.asList(customResponseHeadersString.split("[ ,]+"));
		}

		// resolving properties
		final String resolvePropertiesSource = StructrApp.getConfigurationValue(OBJECT_RESOLUTION_PROPERTIES, "AbstractNode.name");
		for (final String src : resolvePropertiesSource.split("[, ]+")) {

			final String name = src.trim();
			if (StringUtils.isNotBlank(name)) {

				possiblePropertyNamesForEntityResolving.add(name);
			}
		}

		this.isAsync = Services.parseBoolean(Services.getBaseConfiguration().getProperty(HttpService.ASYNC), true);
	}

	@Override
	public void destroy() {
	}

	@Override
	protected void doGet(final HttpServletRequest request, final HttpServletResponse response) {

		try {
			final String path = request.getPathInfo();
			
			final ServletOutputStream out = response.getOutputStream();
			
			String address = request.getParameter("url");
			String username = request.getParameter("username");
			String password = request.getParameter("password");
			String cookie   = request.getParameter("cookie");

			//long t0 = System.currentTimeMillis();

			final HttpClientParams params = new HttpClientParams(HttpClientParams.getDefaultParams());
			final HttpClient client = new HttpClient(params);
			final GetMethod get = new GetMethod(address);

			get.addRequestHeader("User-Agent", "curl/7.35.0");
			get.addRequestHeader("Connection", "close");
			get.getParams().setParameter("http.protocol.single-cookie-header", true);
			get.getParams().setCookiePolicy(CookiePolicy.BROWSER_COMPATIBILITY);
			get.setFollowRedirects(true);
			
			if (StringUtils.isNotBlank(cookie)) {
				
				get.addRequestHeader("Cookie", cookie);
			}

			client.executeMethod(get);

			final String content = get.getResponseBodyAsString().replace("<head>", "<head>\n  <base href=\"" + address + "\">");

			IOUtils.write(content, out);

		} catch (Throwable t) {

			logger.log(Level.SEVERE, "Exception while processing request", t);
			UiAuthenticator.writeInternalServerError(response);
		}
	}

	@Override
	protected void doPost(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {

		doGet(request, response);

	}

	@Override
	protected void doHead(final HttpServletRequest request, final HttpServletResponse response) {

		try {
			String path = request.getPathInfo();


		} catch (Throwable t) {

			logger.log(Level.SEVERE, "Exception while processing request", t);
			UiAuthenticator.writeInternalServerError(response);
		}
	}

	@Override
	protected void doOptions(final HttpServletRequest request, final HttpServletResponse response) {

		final Authenticator auth = config.getAuthenticator();

		try {

			response.setContentLength(0);
			response.setHeader("Allow", "GET,HEAD,OPTIONS");

		} catch (Throwable t) {

			logger.log(Level.SEVERE, "Exception while processing request", t);
			UiAuthenticator.writeInternalServerError(response);
		}
	}


	//~--- set methods ----------------------------------------------------
	public static void setNoCacheHeaders(final HttpServletResponse response) {

		response.setHeader("Cache-Control", "private, max-age=0, s-maxage=0, no-cache, no-store, must-revalidate"); // HTTP 1.1.
		response.setHeader("Pragma", "no-cache, no-store"); // HTTP 1.0.
		response.setDateHeader("Expires", 0);

	}

	private static void setCustomResponseHeaders(final HttpServletResponse response) {

		for (final String header : customResponseHeaders) {

			final String[] keyValuePair = header.split("[ :]+");
			response.setHeader(keyValuePair[0], keyValuePair[1]);

			logger.log(Level.FINE, "Set custom response header: {0} {1}", new Object[]{keyValuePair[0], keyValuePair[1]});

		}

	}

	private static boolean notModifiedSince(final HttpServletRequest request, HttpServletResponse response, final AbstractNode node, final boolean dontCache) {

		boolean notModified = false;
		final Date lastModified = node.getLastModifiedDate();

		// add some caching directives to header
		// see http://weblogs.java.net/blog/2007/08/08/expires-http-header-magic-number-yslow
		final DateFormat httpDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
		httpDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));

		response.setHeader("Date", httpDateFormat.format(new Date()));

		final Calendar cal = new GregorianCalendar();
		final Integer seconds = node.getProperty(Page.cacheForSeconds);

		if (!dontCache && seconds != null) {

			cal.add(Calendar.SECOND, seconds);
			response.setHeader("Cache-Control", "max-age=" + seconds + ", s-maxage=" + seconds + "");
			response.setHeader("Expires", httpDateFormat.format(cal.getTime()));

		} else {

			if (!dontCache) {
				response.setHeader("Cache-Control", "no-cache, must-revalidate, proxy-revalidate");
			} else {
				response.setHeader("Cache-Control", "private, no-cache, no-store, max-age=0, s-maxage=0, must-revalidate, proxy-revalidate");
			}

		}

		if (lastModified != null) {

			final Date roundedLastModified = DateUtils.round(lastModified, Calendar.SECOND);
			response.setHeader("Last-Modified", httpDateFormat.format(roundedLastModified));

			final String ifModifiedSince = request.getHeader("If-Modified-Since");

			if (StringUtils.isNotBlank(ifModifiedSince)) {

				try {

					Date ifModSince = httpDateFormat.parse(ifModifiedSince);

					// Note that ifModSince has not ms resolution, so the last digits are always 000
					// That requires the lastModified to be rounded to seconds
					if ((ifModSince != null) && (roundedLastModified.equals(ifModSince) || roundedLastModified.before(ifModSince))) {

						notModified = true;

						response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
						response.setHeader("Vary", "Accept-Encoding");

					}

				} catch (ParseException ex) {
					logger.log(Level.WARNING, "Could not parse If-Modified-Since header", ex);
				}

			}

		}

		return notModified;
	}


}
