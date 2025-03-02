package org.nhindirect.smtpmq.gateway.springconfig;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.List;

import org.nhindirect.smtpmq.gateway.server.GetMessageHeaderStream;
import org.nhindirect.smtpmq.gateway.server.SMTPMessageHandler;
import org.nhindirect.smtpmq.gateway.server.SizeLimitedInputStreamFactory;
import org.nhindirect.smtpmq.gateway.server.SizeLimitedStreamCreator;
import org.nhindirect.smtpmq.gateway.server.WhitelistedServerSocket;
import org.nhindirect.smtpmq.gateway.streams.SmtpGatewayMessageSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.subethamail.smtp.MessageContext;
import org.subethamail.smtp.MessageHandler;
import org.subethamail.smtp.MessageHandlerFactory;
import org.subethamail.smtp.server.SMTPServer;

@Configuration
public class SMTPServerBeanConfig
{	
	
	@Value("${direct.smtpmqgateway.binding.port:1026}")
	public int port;
	
	@Value("${direct.smtpmqgateway.binding.host:0.0.0.0}")
	public String host;	

	@Value("${direct.smtpmqgateway.mq.exchange:}")
	private String exchange;
	
	@Value("${direct.smtpmqgateway.message.maxHeaderSize:262144}")
	private int maxHeaderSize;			
	 
	@Value("${direct.smtpmqgateway.message.maxMessageSize:39845888}")
	private int maxMessageSize;			
	
	
	@Value("${direct.smtpmqgateway.clientwhitelist.cidr:}")
	private List<String> clientWhitelistCidrs;	
	
	@Autowired
	protected SmtpGatewayMessageSource messageSourceQueue;
	
	@Bean(destroyMethod = "stop")
    public SMTPServer smtpServer() throws Exception
    {
		final SMTPServer smtpServer = new SMTPServer(new MessageHandlerFactory() 
	    {
			@Override
			public MessageHandler create(MessageContext ctx) 
			{
				return smtpMessageHandler();		
			}
	        
        })
		{
			@Override
			protected ServerSocket createServerSocket() throws IOException
			{
				if (clientWhitelistCidrs.isEmpty() || 
						(clientWhitelistCidrs.size() == 1 && !StringUtils.hasText(clientWhitelistCidrs.get(0))))
					return super.createServerSocket();
				
				InetSocketAddress isa;
	
				if (this.getBindAddress() == null)
				{
					isa = new InetSocketAddress(this.getPort());
				}
				else
				{
					isa = new InetSocketAddress(this.getBindAddress(), this.getPort());
				}
	
				final ServerSocket serverSocket = new WhitelistedServerSocket(clientWhitelistCidrs);
				serverSocket.bind(isa, this.getBacklog());
	
				if (this.getPort() == 0)
				{
					this.setPort(serverSocket.getLocalPort());
				}
	
				return serverSocket;
			}				
		};
		
		smtpServer.setPort(port);
		smtpServer.setBindAddress(InetAddress.getByName(host));
		smtpServer.setSoftwareName("DirectProject Java RI SMTP To MQ Gateway");
		smtpServer.setMaxMessageSize(maxMessageSize);
		return smtpServer;
    }
	
	@Bean
	public SMTPMessageHandler smtpMessageHandler()
	{	
		final SizeLimitedStreamCreator sizeCreator = new SizeLimitedStreamCreator(maxMessageSize,
				SizeLimitedInputStreamFactory.getInstance());	
		
		return new SMTPMessageHandler(messageSourceQueue, new GetMessageHeaderStream(maxHeaderSize), sizeCreator);
	}		
}
