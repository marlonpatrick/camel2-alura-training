package br.com.caelum.camel;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Service;

@Service
public class ProdutoService extends RouteBuilder {

	@Override
	public void configure() throws Exception {
		System.out.println("ProdutoService...");
		from("file:pedidos").to("activemq-camel-alura:queue:pedidos");
	}
}
