/**
 * This script performs a cleanup of release repositories.
 * Here are the parameters that control the cleanup behaviour:
 *   daysInterval: how many days back should artifacts be kept
 *   componentsToKeep: least amount of artifacts to keep
 *   skeppedRepos: repositories to skip cleaning entirely
 *
 * Each repository is scanned, and for all components with identical groupId:artifactId strings
 * a hashmap is constructed to hold all matched components and last update times.
 * Then, skipping ${componentsToKeep} components, each component is deleted given that its
 * last update time is before ${daysInterval}.
 * Notice that artifacts older than ${daysInterval} may remain after cleanup, given these were
 * skipped by the ${componentsToKeep} parameter.
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
 *   Cleanup repository components (skipping ${skippedRepos}) older than ${daysInterval} days,
 *   keeping ${componentsToKeep}.
 */
int daysInterval = 30
int componentsToKeep = 1
def skippedRepos = [ "thirdparty" ]

int secondsInterval = TimeUnit.DAYS.toSeconds(daysInterval)

log.info("**********************************")
log.info("daysInterval: ${daysInterval}, secondsInterval: ${secondsInterval}")
log.info("componentsToKeep: ${componentsToKeep}")
log.info("skippedRepos: ${skippedRepos}")
log.info("**********************************")

class ReverseDateTimeComparator implements Comparator<DateTime> {
    @Override
    int compare(DateTime o1, DateTime o2) {
        return o2.compareTo(o1)
    }
}

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

        log.info("Collecting components history")
        HashMap<String, SortedMap<DateTime, Component>> artifacts = new HashMap<String, SortedMap<DateTime, Component>>()
        SortedMap<DateTime, Component> sortedComponents
        ReverseDateTimeComparator reverseComparator = new ReverseDateTimeComparator()
        String gaString
        for (Component component : storageTx.browseComponents(storageTx.findBucket(repository))) {
            gaString = sprintf("%s:%s", [component.group(), component.name()])
            if (artifacts.containsKey(gaString)) {
                sortedComponents = artifacts.get(gaString)
                sortedComponents.put(component.lastUpdated(), component)

            } else {// first time
                sortedComponents = new TreeMap<DateTime, Component>(reverseComparator)
                sortedComponents.put(component.lastUpdated(), component)
                artifacts.put(gaString, sortedComponents)
            }
        }
        log.info("Found {} artifacts (groupId:artifactId)", artifacts.size())


        Component component
        int counter
        for (String artifactString : artifacts.keySet()) {
            log.info("Processing artifact {}", artifactString)
            sortedComponents = artifacts.get(artifactString)
            Iterator componentsIterator = sortedComponents.iterator()
            counter = 0
            while (componentsIterator.hasNext() && counter < componentsToKeep) {
                componentsIterator.next()
                counter++
            }

            while (componentsIterator.hasNext()) {
                component = componentsIterator.next().getValue()
                if (DateTime.now().minusSeconds(secondsInterval).isAfter(component.lastUpdated())) {
                    log.info("Deleting component: {}:{}:{}", component.group(), component.name(), component.version())
                    storageTx.deleteComponent(component)
                }
            }
        }

        storageTx.commit()

        if (repository.getFormat().getValue().equals('maven2') && repository.getType().equals(new HostedType())) {
            log.info("Rebuilding Maven 2 repository index")
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
