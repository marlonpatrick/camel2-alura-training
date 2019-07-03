package br.com.caelum.camel;

import java.text.SimpleDateFormat;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dataformat.xstream.XStreamDataFormat;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;

import com.mysql.jdbc.jdbc2.optional.MysqlConnectionPoolDataSource;
import com.thoughtworks.xstream.XStream;

public class RotaHttpPollingNegociacoes {

	public static void main(String[] args) throws Exception {

		SimpleRegistry registro = new SimpleRegistry();
		registro.put("mysql-camel-alura", createDataSource());
		CamelContext context = new DefaultCamelContext(registro);
		
		context.addRoutes(new RouteBuilder() { // cuidado, não é RoutesBuilder

			@Override
			public void configure() throws Exception {
				
				XStream xStreamNegociacao = new XStream();
				xStreamNegociacao.alias("negociacao", Negociacao.class);
				
				from("timer://negociacoes?fixedRate=true&delay=1s&period=360s")
				.to("http4://argentumws-spring.herokuapp.com/negociacoes")
				.convertBodyTo(String.class)
				.unmarshal(new XStreamDataFormat(xStreamNegociacao))
				.log("Log 1 - ${id} - ${body}")
				.split(body())
				.log("Log 2 - ${id} - ${body}")
				.process(new Processor() {
			        @Override
			        public void process(Exchange exchange) throws Exception {
			            Negociacao negociacao = exchange.getIn().getBody(Negociacao.class);
			            exchange.setProperty("preco", negociacao.getPreco());
			            exchange.setProperty("quantidade", negociacao.getQuantidade());
			            String data = new SimpleDateFormat("YYYY-MM-dd HH:mm:ss").format(negociacao.getData().getTime());
			            exchange.setProperty("data", data);
			        }
			      })
			      .setBody(simple("insert into negociacao(preco, quantidade, data) values (${property.preco}, ${property.quantidade}, '${property.data}')"))
			      .log("Log 3 - ${id} - ${body}")
			      .delay(1000)
			      .to("jdbc:mysql-camel-alura");
			}
		});
		
		context.start();

        Thread.sleep(20000);
	}
	
	private static MysqlConnectionPoolDataSource createDataSource() {
	    MysqlConnectionPoolDataSource mysqlDs = new MysqlConnectionPoolDataSource();
	    mysqlDs.setDatabaseName("camel");
	    mysqlDs.setServerName("localhost");
	    mysqlDs.setPort(3306);
	    mysqlDs.setUser("camel");
	    mysqlDs.setPassword("camel");
	    return mysqlDs;
	}
}
