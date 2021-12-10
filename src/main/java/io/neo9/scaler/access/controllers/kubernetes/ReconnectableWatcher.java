package io.neo9.scaler.access.controllers.kubernetes;

public interface ReconnectableWatcher {

	void startWatch(ReconnectableControllerOrchestrator reconnectableControllerOrchestrator);

	void stopWatch();

}
