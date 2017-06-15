package org.centauri.cloud.cloud.loadbalancing;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;
import org.centauri.cloud.cloud.Cloud;
import org.centauri.cloud.cloud.config.PropertyManager;
import org.centauri.cloud.cloud.event.events.RequestServerEvent;
import org.centauri.cloud.cloud.server.Daemon;
import org.centauri.cloud.cloud.server.Server;
import org.centauri.cloud.cloud.server.SpigotServer;
import org.centauri.cloud.cloud.template.Template;

public class ServerLoadBalancer extends TimerTask {

	public void initializeScheduler() {
		new Timer("ServerLoadBalancer").scheduleAtFixedRate(this, 1000L, Integer.valueOf(PropertyManager.getInstance().getProperties().getProperty("loadbalancerIntervall", "30")) * 1000L);
	}
	
	@Override
	public void run() {
		Map<String, Set<SpigotServer>> prefixToServers = new HashMap<>();
		Cloud.getInstance().getServerManager().getChannelToServer().values().forEach(server -> {
			if(!(server instanceof SpigotServer))
				return;
			
			if(!prefixToServers.containsKey(server.getPrefix()))
				prefixToServers.put(server.getPrefix(), new HashSet<>());
			
			prefixToServers.get(server.getPrefix()).add((SpigotServer) server);
		});
		
		Cloud.getInstance().getTemplateManager().getTemplates().forEach(template -> {
			Set<SpigotServer> freeServers = new HashSet<>();
			
			Set<SpigotServer> servers = prefixToServers.get(template.getName());
			if(servers == null) {
				ServerLoadBalancer.this.requestServer(template);
				return;
			}
			
			servers.forEach(server -> {
				if(server.getPlayers() < template.getMaxPlayers())
					freeServers.add(server);
			});
			
			if(freeServers.size() < template.getMinServersFree()) {
				ServerLoadBalancer.this.requestServer(template);
			}
		});
		
	}
	
	public void requestServer(Template template) {
		//TODO: Find beste daemon(lowest load)
		List<Server> daemons =  Cloud.getInstance().getServerManager().getChannelToServer().values().stream().filter(server -> server instanceof Daemon).collect(Collectors.toList());
		if(daemons.isEmpty())
			return;
		Daemon daemon = (Daemon) daemons.get(0);
		daemon.startServer(template.getName());
		Cloud.getInstance().getEventManager().callEvent(new RequestServerEvent(template));
	}
	
}