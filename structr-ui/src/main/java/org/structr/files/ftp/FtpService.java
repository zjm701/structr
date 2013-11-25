/*
 *  Copyright (C) 2013 Axel Morgner
 * 
 *  This file is part of structr <http://structr.org>.
 * 
 *  structr is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 * 
 *  structr is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 *  You should have received a copy of the GNU Affero General Public License
 *  along with structr.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.structr.files.ftp;

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang.StringUtils;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.listener.ListenerFactory;
import org.structr.core.Command;
import org.structr.core.RunnableService;
import org.structr.core.Services;

/**
 *
 * @author axel
 */
public class FtpService implements RunnableService {

	private static final Logger logger = Logger.getLogger(FtpService.class.getName());
	private boolean isRunning          = false;
	
	private static int port;
	private FtpServer server;

	@Override
	public void startService() {
		
		try {
			
			FtpServerFactory serverFactory = new FtpServerFactory();
			
			serverFactory.setUserManager(new StructrUserManager());
			serverFactory.setFileSystem( new StructrFileSystemFactory());
			
			ListenerFactory factory = new ListenerFactory();
			factory.setPort(port);
			serverFactory.addListener("default", factory.createListener());
			
			logger.log(Level.INFO, "Starting FTP server on port {0}", new Object[] {port});

			server = serverFactory.createServer();         
			server.start();
			
			this.isRunning = true;
			
		} catch (FtpException ex) {
			
			logger.log(Level.SEVERE, null, ex);
		}
		
	}

	@Override
	public void stopService() {
		
		if (isRunning) {
			this.shutdown();
		}
	}

	@Override
	public boolean runOnStartup() {
		return true;
	}

	@Override
	public boolean isRunning() {
		return !server.isStopped();
	}

	@Override
	public void injectArguments(Command command) {
	}

	@Override
	public void initialize(final Properties configurationFile) {
		
		final String configuredPort = configurationFile.getProperty(Services.APPLICATION_FTP_PORT);
		
		try {
			port = Integer.parseInt(configuredPort);
			
		} catch (Throwable t) {
			
			logger.log(Level.SEVERE, "Unable to parse FTP port {0}", configuredPort);
			
			port = -1;
		}

		if (port == -1) {
			logger.log(Level.SEVERE, "Unable to start FTP service.");
		}
	}

	@Override
	public void shutdown() {
		if (!server.isStopped()) {
			server.stop();
			this.isRunning = false;
		}
	}

	@Override
	public String getName() {
		return FtpServer.class.getSimpleName();
	}
}
