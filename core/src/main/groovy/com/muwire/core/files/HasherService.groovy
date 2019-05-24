package com.muwire.core.files

import java.util.concurrent.Executor
import java.util.concurrent.Executors

import com.muwire.core.EventBus
import com.muwire.core.SharedFile

class HasherService {

	final FileHasher hasher
	final EventBus eventBus
	Executor executor
	
	HasherService(FileHasher hasher, EventBus eventBus) {
		this.hasher = hasher
		this.eventBus = eventBus
	}
	
	void start() {
		executor = Executors.newSingleThreadExecutor()
	}
	
	void onFileSharedEvent(FileSharedEvent evt) {
		executor.execute( { -> process(evt.file) } as Runnable)
	}
	
	private void process(File f) {
		f = f.getCanonicalFile()
		if (f.isDirectory()) {
			f.listFiles().each {onFileSharedEvent new FileSharedEvent(file: it) }
		} else {
			if (f.length() == 0) {
				eventBus.publish new FileHashedEvent(error: "Not sharing empty file $f")
			} else if (f.length() > FileHasher.MAX_SIZE) {
				eventBus.publish new FileHashedEvent(error: "$f is too large to be shared ${f.length()}")
			} else {
				def hash = hasher.hashFile f
				eventBus.publish new FileHashedEvent(sharedFile: new SharedFile(f, hash))
			}
		}
	}
}
