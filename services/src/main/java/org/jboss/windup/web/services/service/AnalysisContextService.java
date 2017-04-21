package org.jboss.windup.web.services.service;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;

import org.jboss.windup.web.services.model.*;
import org.jboss.windup.web.services.model.Package;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Provides tools for creating default analysis context instances, as well as providing default configuration data.
 *
 * @author <a href="mailto:jesse.sightler@gmail.com">Jesse Sightler</a>
 */
public class AnalysisContextService
{
    @PersistenceContext
    private EntityManager entityManager;

    @Inject
    private ConfigurationService configurationService;

    /**
     * Gets analysis context
     */
    public AnalysisContext get(Long id)
    {
        AnalysisContext context = entityManager.find(AnalysisContext.class, id);

        if (context == null)
        {
            throw new NotFoundException("AnalysisContext with id" + id + "not found");
        }

        return context;
    }

    /**
     * Creates a default instance.
     */
    public AnalysisContext createDefaultAnalysisContext(MigrationProject project)
    {
        AnalysisContext defaultAnalysisContext = new AnalysisContext(project);
        ensureSystemRulesPathsPresent(defaultAnalysisContext);
        entityManager.persist(defaultAnalysisContext);

        return defaultAnalysisContext;
    }

    /**
     * Creates a new instance.
     */
    public AnalysisContext create(AnalysisContext analysisContext)
    {
        analysisContext.setId(null); // creating new instance, should not have id
        this.ensureSystemRulesPathsPresent(analysisContext);
        this.loadPackagesToAnalysisContext(analysisContext);
        this.loadAdvancedOptionsToAnalysisContext(analysisContext);
        entityManager.persist(analysisContext);

        return analysisContext;
    }

    protected void loadAdvancedOptionsToAnalysisContext(AnalysisContext analysisContext)
    {
        analysisContext.setAdvancedOptions(this.loadAdvancedOptionsFromPersistenceContext(analysisContext.getAdvancedOptions()));
    }

    protected Collection<AdvancedOption> loadAdvancedOptionsFromPersistenceContext(Collection<AdvancedOption> advancedOptions)
    {
        return advancedOptions.stream()
                .filter(anOption -> anOption.getId() != null && anOption.getId() != 0)
                .map(anOption -> this.entityManager.find(AdvancedOption.class, anOption.getId()))
                .filter(anOption -> anOption != null)
                .map(anOption -> new AdvancedOption(anOption.getName(), anOption.getValue()))
                .collect(Collectors.toList());
    }

    protected void loadPackagesToAnalysisContext(AnalysisContext analysisContext)
    {
        analysisContext.setIncludePackages(this.loadPackagesFromPersistenceContext(analysisContext.getIncludePackages()));
        analysisContext.setExcludePackages(this.loadPackagesFromPersistenceContext(analysisContext.getExcludePackages()));
    }

    protected Set<Package> loadPackagesFromPersistenceContext(Collection<Package> detachedPackages)
    {
        return detachedPackages.stream()
                .filter(aPackage -> aPackage.getId() != null && aPackage.getId() != 0)
                .map(aPackage -> this.entityManager.find(Package.class, aPackage.getId()))
                .filter(aPackage -> aPackage != null)
                .collect(Collectors.toSet());
    }

    protected boolean contextHasExecutions(AnalysisContext context)
    {
        String query = "SELECT COUNT(ex) FROM WindupExecution ex WHERE ex.analysisContext = :ctxt";


        Long count = this.entityManager.createQuery(query, Long.class)
            .setParameter("ctxt", context)
            .getSingleResult();

        return count > 0;
    }

    /**
     * Updates an existing instance.
     */
    public AnalysisContext update(Long analysisContextId, AnalysisContext analysisContext)
    {
        AnalysisContext original = this.get(analysisContextId);

        if (this.contextHasExecutions(original))
        {
            throw new BadRequestException("Cannot update context used for executions");
        }

        analysisContext.setId(analysisContextId); // make sure user doesn't provide invalid id
        this.ensureSystemRulesPathsPresent(analysisContext);
        this.loadPackagesToAnalysisContext(analysisContext);
        analysisContext.setMigrationProject(original.getMigrationProject());

        return entityManager.merge(analysisContext);
    }

    private void ensureSystemRulesPathsPresent(AnalysisContext analysisContext)
    {
        this.configurationService
                    .getConfiguration().getRulesPaths()
                    .stream()
                    .filter(rulesPath -> rulesPath.getRulesPathType() == RulesPath.RulesPathType.SYSTEM_PROVIDED)
                    .forEach(rulesPath -> {
                        if (analysisContext.getRulesPaths() == null)
                            analysisContext.setRulesPaths(new HashSet<>());

                        if (!analysisContext.getRulesPaths().contains(rulesPath))
                            analysisContext.getRulesPaths().add(rulesPath);
                    });
    }
}
