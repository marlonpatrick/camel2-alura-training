package br.com.caelum.camel;

import org.apache.activemq.camel.component.ActiveMQComponent;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.xml.sax.SAXParseException;

public class RotaPedidos {

	public static void main(String[] args) throws Exception {

		CamelContext context = new DefaultCamelContext();
		context.addComponent("activemq-camel-alura", ActiveMQComponent.activeMQComponent("tcp://localhost:61616"));
		context.addRoutes(new RouteBuilder() {

			@Override
			public void configure() throws Exception {

				deadLetterChanelErrorHandler();

				buildHttpDirectRoute();
				
				buildSoapDirectRoute();

				from("activemq-camel-alura:queue:pedidos")
			    .routeId("rota-pedidos")
			    .to("validator:pedido.xsd")
					.multicast()//.parallelProcessing().timeout(500)
					.to("direct:soap")
					.to("direct:http");
//					.to("seda:soap")
//					.to("seda:http");
				
			}

			private void deadLetterChanelErrorHandler() {
				errorHandler(
						deadLetterChannel("activemq-camel-alura:queue:pedidos.DLQ")
							.useOriginalMessage()
							.logExhaustedMessageHistory(true)
							.maximumRedeliveries(3)
							.redeliveryDelay(2000)
							.onRedelivery(ex -> {
								int counter = (int) ex.getIn().getHeader(Exchange.REDELIVERY_COUNTER);
							    int max = (int) ex.getIn().getHeader(Exchange.REDELIVERY_MAX_COUNTER);
							    System.out.println("errorHandler: Redelivery - " + counter + "/" + max );
							}));
			}

			@SuppressWarnings("unused")
			private void onExceptionHandler() {
				onException(SAXParseException.class)
			    	.handled(true)
			        .maximumRedeliveries(3)
			        .redeliveryDelay(1000)
			        .onRedelivery(ex -> {
	                    int counter = (int) ex.getIn().getHeader(Exchange.REDELIVERY_COUNTER);
	                    int max = (int) ex.getIn().getHeader(Exchange.REDELIVERY_MAX_COUNTER);
	                    System.out.println("onException: Redelivery - " + counter + "/" + max );;
			        });
			}

			private void buildSoapDirectRoute() {
				from("direct:soap")
//				from("seda:soap")
			    	.routeId("rota-soap")
			    	.to("xslt:pedido-para-soap.xslt")
			        .log("Resultado do Template: ${body}")
			        .setHeader(Exchange.CONTENT_TYPE, constant("text/xml"))
			        .to("http4://localhost:8080/webservices/financeiro");
			}

			private void buildHttpDirectRoute() {
				from("direct:http")
//				from("seda:http")
					.routeId("rota-http")
					.setProperty("pedidoId", xpath("/pedido/id/text()"))
				    .setProperty("clienteId", xpath("/pedido/pagamento/email-titular/text()"))
					.split().xpath("/pedido/itens/item")
					.filter().xpath("/item/formato[text()='EBOOK']")
				    .setProperty("ebookId", xpath("/item/livro/codigo/text()"))
					.marshal().xmljson()
					.log("${id} - ${body}")
					.setHeader(Exchange.HTTP_QUERY, 
				            simple("clienteId=${property.clienteId}&pedidoId=${property.pedidoId}&ebookId=${property.ebookId}"))
					.to("http4://localhost:8080/webservices/ebook/item");
			}
		});
		
		context.start();

        Thread.sleep(20000);
	}
}
