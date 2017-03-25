package hr.hrg.watch.build;

import ch.qos.logback.core.ConsoleAppender;

public class WsAndConsoleAppender<E> extends ConsoleAppender<E>{

	@Override
	protected void append(E event) {
		super.append(event);
		byte[] bytes = this.encoder.encode(event);
//		System.err.write(bytes,0,bytes.length);
	}
}
