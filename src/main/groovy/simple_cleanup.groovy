/**
 * This script performs a cleanup of release repositories.
 * Here are the parameters that control the cleanup behaviour:
 *   daysInterval: how many days back should artifacts be kept
 *   skeppedRepos: repositories to skip cleaning entirely
 *
 * Each repository is scanned, and each component found is deleted if its last update
 * time is before the ${daysInterval} period.
 *
 */
import org.joda.time.DateTime
import org.sonatype.nexus.blobstore.api.BlobStore
import org.sonatype.nexus.blobstore.api.BlobStoreManager
import org.sonatype.nexus.repository.Repository
import org.sonatype.nexus.repository.manager.RepositoryManager
import org.sonatype.nexus.repository.search.SearchFacet
import org.sonatype.nexus.repository.storage.Component
import org.sonatype.nexus.repository.storage.StorageFacet
import org.sonatype.nexus.repository.storage.StorageTx
import org.sonatype.nexus.repository.types.HostedType

import java.util.concurrent.TimeUnit

/**
 * Parameters:
 *   Cleanup repository components (skipping ${skippedRepos}) older than ${daysInterval} days.
 */
int daysInterval = 30
def skippedRepos = [ "thirdparty" ]

int secondsInterval = TimeUnit.DAYS.toSeconds(daysInterval)

log.info("**********************************")
log.info("daysInterval: ${daysInterval}, secondsInterval: ${secondsInterval}")
log.info("skippedRepos: ${skippedRepos}")
log.info("**********************************")

/**
 * Cleanup
 */
RepositoryManager repoManager = repository.repositoryManager
for (Repository repository : repoManager.browse()) {
    if (skippedRepos.contains(repository.getName())) {
        log.info("Skipping repository: {}", repository.getName())
        continue
    }

    log.info("Cleaning repository {}", repository.toString())
    StorageTx storageTx = repository.facet(StorageFacet).txSupplier().get()
    try {
        storageTx.begin()
        for (Component component : storageTx.browseComponents(storageTx.findBucket(repository))) {
            if (DateTime.now().minusSeconds(secondsInterval).isAfter(component.lastUpdated())) {
                log.info("Deleting component: {}:{}:{}", component.group(), component.name(), component.version())
                storageTx.deleteComponent(component)
            }
        }

        storageTx.commit()

        if (repository.getFormat().getValue().equals('maven2') && repository.getType().equals(new HostedType())) {
            repository.facet(SearchFacet).rebuildIndex()
        }
    }
    catch (Exception e) {
        log.warn("Cleanup failed!!!")
        log.warn("Exception details: {}", e.toString())
        log.warn("Rolling back storage transaction")
        storageTx.rollback()
    }
    finally {
        storageTx.close()
    }
}

// compact blob stores
BlobStoreManager manager = blobStore.blobStoreManager
for (BlobStore store : manager.browse()) {
    log.info("Compacting blobstore: {} - START", store.toString())
    store.compact()
    log.info("Compacting blobstore: {} - FINISH", store.toString())
}
