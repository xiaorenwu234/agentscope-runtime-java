package io.agentscope.examples.client;

import io.agentscope.core.a2a.agent.A2aAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.runtime.cluster.nacos.NacosAgentCardResolver;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class A2aClientTest {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("A2A Cross-Framework Client Test");
        System.out.println("========================================\n");

        // Get Nacos configuration from user
        Scanner scanner = new Scanner(System.in);
        System.out.println("Configuring Nacos connection...");

        String nacosServer = System.getenv().getOrDefault("NACOS_SERVER_ADDR",
                System.getProperty("nacos.server.addr", "localhost:8848"));
        String namespace = System.getenv().getOrDefault("NACOS_NAMESPACE",
                System.getProperty("nacos.namespace", "public"));
        String group = System.getenv().getOrDefault("NACOS_GROUP",
                System.getProperty("nacos.group", "DEFAULT_GROUP"));
        
        // Discover services from Nacos
        NacosAgentCardResolver nacosResolver = NacosAgentCardResolver.builder()
                .serverAddr(nacosServer)
                .namespace(namespace)
                .group(group)
                .relativeCardPath("/.well-known/agent.json")
                .build();
        
        List<String> availableServices = nacosResolver.discoverServices();
        
        if (availableServices.isEmpty()) {
            System.out.println("No services found in Nacos. Exiting.");
            scanner.close();
            return;
        }
        
        // Display available services
        System.out.println("\nAvailable services in Nacos:");
        for (int i = 0; i < availableServices.size(); i++) {
            System.out.println((i + 1) + ". " + availableServices.get(i));
        }
        
        // Let user select which service(s) to use
        System.out.println("\nSelect services to communicate with (comma-separated numbers, or 'all' for all):");
        System.out.print("Your selection: ");
        String selection = scanner.nextLine().trim();
        
        List<A2aAgent> selectedAgents = new ArrayList<>();
        
        if ("all".equalsIgnoreCase(selection)) {
            // Create agents for all services
            for (String serviceName : availableServices) {
                selectedAgents.add(createAgentWithNacos(nacosResolver, serviceName));
            }
        } else {
            // Parse selection and create agents for selected services
            String[] selections = selection.split(",");
            for (String sel : selections) {
                try {
                    int index = Integer.parseInt(sel.trim()) - 1;
                    if (index >= 0 && index < availableServices.size()) {
                        String serviceName = availableServices.get(index);
                        selectedAgents.add(createAgentWithNacos(nacosResolver, serviceName));
                    } else {
                        System.out.println("Invalid selection: " + sel);
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Invalid number: " + sel);
                }
            }
        }
        
        if (selectedAgents.isEmpty()) {
            System.out.println("No valid services selected. Exiting.");
            scanner.close();
            return;
        }
        
        System.out.println("\nSuccessfully created agents for " + selectedAgents.size() + " service(s).");

        while (true) {
            System.out.println("\nSelect a server to communicate with:");
            for (int i = 0; i < selectedAgents.size(); i++) {
                System.out.println((i + 1) + ". " + selectedAgents.get(i).getName());
            }
            System.out.println((selectedAgents.size() + 1) + ". Test all selected servers with same message");
            System.out.println((selectedAgents.size() + 2) + ". Exit");
            System.out.print("Enter your choice (1-" + (selectedAgents.size() + 2) + "): ");

            String choice = scanner.nextLine();
            int choiceNum;
            try {
                choiceNum = Integer.parseInt(choice);
            } catch (NumberFormatException e) {
                System.out.println("Invalid choice. Please try again.");
                continue;
            }

            if (choiceNum == selectedAgents.size() + 2) {
                System.out.println("Goodbye!");
                break;
            }

            System.out.print("Enter your message: ");
            String message = scanner.nextLine();

            try {
                if (choiceNum == selectedAgents.size() + 1) {
                    // Test all selected servers
                    System.out.println("\n>>> Sending to all selected servers...");
                    for (A2aAgent agent : selectedAgents) {
                        System.out.println("\n--- " + agent.getName() + " Response ---");
                        callAgent(agent, message);
                    }
                } else if (choiceNum >= 1 && choiceNum <= selectedAgents.size()) {
                    // Call specific agent
                    A2aAgent agent = selectedAgents.get(choiceNum - 1);
                    System.out.println("\n>>> Sending to " + agent.getName() + "...");
                    callAgent(agent, message);
                } else {
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
     * Create A2aAgent with Nacos discovery for a specific service.
     */
    private static A2aAgent createAgentWithNacos(NacosAgentCardResolver nacosResolver, String serviceName) {
        System.out.println("Creating A2aAgent with Nacos discovery for service: " + serviceName);

        return A2aAgent.builder()
                .name(serviceName)
                .agentCardResolver(nacosResolver)
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
