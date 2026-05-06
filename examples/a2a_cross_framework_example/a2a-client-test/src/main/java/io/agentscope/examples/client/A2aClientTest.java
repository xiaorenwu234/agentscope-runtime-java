package io.agentscope.examples.client;

import io.agentscope.core.a2a.agent.A2aAgent;
import io.agentscope.core.a2a.agent.card.WellKnownAgentCardResolver;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import reactor.core.publisher.Flux;

import java.util.Scanner;

/**
 * A2A Client Test Application.
 * 
 * <p>This application demonstrates how to use A2aAgent to communicate with different A2A servers:
 * 1. Spring AI Alibaba A2A Server (port 10002)
 * 2. AgentScope A2A Server (port 10001)
 * 
 * <p>The client discovers agent capabilities through AgentCard and communicates via A2A JSON-RPC protocol.</p>
 *
 * @author Agentscope Team
 */
public class A2aClientTest {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("A2A Cross-Framework Client Test");
        System.out.println("========================================\n");

        // Create A2A agents for both servers
        A2aAgent saaAgent = createSaaAgent();
        A2aAgent agentScopeAgent = createAgentScopeAgent();

        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("\nSelect a server to communicate with:");
            System.out.println("1. Spring AI Alibaba A2A Server (port 10002)");
            System.out.println("2. AgentScope A2A Server (port 10001)");
            System.out.println("3. Test both servers with same message");
            System.out.println("4. Exit");
            System.out.print("Enter your choice (1-4): ");

            String choice = scanner.nextLine();

            if ("4".equals(choice)) {
                System.out.println("Goodbye!");
                break;
            }

            System.out.print("Enter your message: ");
            String message = scanner.nextLine();

            try {
                switch (choice) {
                    case "1":
                        System.out.println("\n>>> Sending to SAA A2A Server...");
                        callAgent(saaAgent, message);
                        break;
                    case "2":
                        System.out.println("\n>>> Sending to AgentScope A2A Server...");
                        callAgent(agentScopeAgent, message);
                        break;
                    case "3":
                        System.out.println("\n>>> Sending to both servers...");
                        System.out.println("\n--- SAA A2A Server Response ---");
                        callAgent(saaAgent, message);
                        System.out.println("\n--- AgentScope A2A Server Response ---");
                        callAgent(agentScopeAgent, message);
                        break;
                    default:
                        System.out.println("Invalid choice. Please try again.");
                }
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                e.printStackTrace();
            }
        }

        scanner.close();
    }

    /**
     * Create A2aAgent for Spring AI Alibaba Server.
     */
    private static A2aAgent createSaaAgent() {
        System.out.println("Creating A2aAgent for SAA Server (http://localhost:10002)...");
        
        // Use WellKnownAgentCardResolver to auto-discover agent capabilities
        WellKnownAgentCardResolver cardResolver = WellKnownAgentCardResolver.builder()
                .baseUrl("http://localhost:10002")
                .relativeCardPath("/.well-known/agent.json")
                .build();

        return A2aAgent.builder()
                .name("saa-assistant")
                .agentCardResolver(cardResolver)
                .build();
    }

    /**
     * Create A2aAgent for AgentScope Server.
     */
    private static A2aAgent createAgentScopeAgent() {
        System.out.println("Creating A2aAgent for AgentScope Server (http://localhost:10001)...");
        
        WellKnownAgentCardResolver cardResolver = WellKnownAgentCardResolver.builder()
                .baseUrl("http://localhost:10001")
                .relativeCardPath("/.well-known/agent.json")
                .build();

        return A2aAgent.builder()
                .name("agentscope-assistant")
                .agentCardResolver(cardResolver)
                .build();
    }

    /**
     * Call agent with streaming response.
     */
    private static void callAgent(A2aAgent agent, String message) {
        Msg userMsg = Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder().text(message).build())
                .build();

        System.out.println("User: " + message);
        System.out.print("Assistant: ");

        // Use streaming to get real-time response
        StringBuilder responseBuilder = new StringBuilder();
        Flux<String> responseFlux = agent.stream(userMsg)
                .filter(event -> event.getMessage() != null)
                .map(event -> {
                    String text = event.getMessage().getTextContent();
                    System.out.println(text);
                    if (text != null && !text.isEmpty()) {
                        responseBuilder.append(text);
                        return text;
                    }
                    return "";
                });

        // Print streaming response
        responseFlux.doOnNext(text -> System.out.print(text))
                .doOnError(error -> System.err.println("\nError: " + error.getMessage()))
                .then()
                .block();

        System.out.println("\n[Response complete]");
    }
}
