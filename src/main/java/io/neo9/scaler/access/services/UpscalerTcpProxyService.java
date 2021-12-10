package io.neo9.scaler.access.services;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.Pod;
import io.neo9.scaler.access.repositories.PodRepository;
import io.neo9.scaler.access.repositories.ServiceRepository;
import io.neo9.scaler.access.utils.common.KubernetesUtils;
import io.neo9.scaler.access.utils.network.TcpServerProxyHandler;
import io.neo9.scaler.access.utils.network.TcpTableParser;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

@Service
@Slf4j
public class UpscalerTcpProxyService {

	private final PodRepository podRepository;

	private final ServiceRepository serviceRepository;

	public UpscalerTcpProxyService(PodRepository podRepository, ServiceRepository serviceRepository) {
		this.podRepository = podRepository;
		this.serviceRepository = serviceRepository;
	}

	public void forwardRequest(NioSocketChannel ctx) {
		String sourcePodIp = ctx.remoteAddress().getAddress().getHostAddress();

		Optional<Pod> podByIp = podRepository.findPodByIp(sourcePodIp);
		if (podByIp.isEmpty()) {
			log.warn("the forwarder proxy received a request from an host ({}) that it couldn't identify, dropping request", sourcePodIp);
			ctx.pipeline().close();
		}
		Pod sourcePod = podByIp.get();
		log.info("forwarding request from {} [{}]", sourcePod.getMetadata().getName(), sourcePodIp);

		String tcpTableRawOutput = podRepository.exec(sourcePod, "istio-proxy", "cat", "/proc/net/tcp");
		List<io.fabric8.kubernetes.api.model.Service> targetedServices = TcpTableParser.parseTCPTable(tcpTableRawOutput).stream()
				.filter(t -> t.getState().equals("1"))   // TCP_ESTABLISHED
				.filter(t -> !t.getUid().equals("1337")) // from our app, not from envoy !
				.map(t -> t.getRemoteAddress())
				.map(ip -> serviceRepository.findServiceByIp(ip))
				.filter(Optional::isPresent)
				.map(opt -> opt.get())
				.collect(Collectors.toList());

		for (io.fabric8.kubernetes.api.model.Service service : targetedServices) {
			String serviceNamespaceAndName = KubernetesUtils.getResourceNamespaceAndName(service);
			log.debug("checking if workload behind service {} [{}] have to upscale", serviceNamespaceAndName, service.getSpec().getClusterIP());
		}

		ctx.pipeline().addLast("ssTcpProxy", new TcpServerProxyHandler());
	}

}
