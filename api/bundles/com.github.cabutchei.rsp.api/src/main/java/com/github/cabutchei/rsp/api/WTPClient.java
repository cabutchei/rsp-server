package com.github.cabutchei.rsp.api;

import com.github.cabutchei.rsp.api.dao.WatchPatternsChangedParams;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment;
@JsonSegment("wtpClient")
public interface WTPClient {
	@JsonNotification
	void watchPatternsChanged(WatchPatternsChangedParams params);
}
