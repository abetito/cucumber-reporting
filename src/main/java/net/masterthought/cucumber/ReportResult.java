package net.masterthought.cucumber;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.lang.StringUtils;

import net.masterthought.cucumber.json.Element;
import net.masterthought.cucumber.json.Feature;
import net.masterthought.cucumber.json.Match;
import net.masterthought.cucumber.json.Result;
import net.masterthought.cucumber.json.Step;
import net.masterthought.cucumber.json.Tag;
import net.masterthought.cucumber.json.support.Resultsable;
import net.masterthought.cucumber.json.support.Status;
import net.masterthought.cucumber.json.support.StatusCounter;
import net.masterthought.cucumber.json.support.StepObject;
import net.masterthought.cucumber.json.support.TagObject;
import net.masterthought.cucumber.reports.OverviewReport;
import net.masterthought.cucumber.reports.Reportable;

public class ReportResult {

	private static final String TOP_LEVEL = "TOP_LEVEL";
	private final List<Feature> allFeatures = new ArrayList<>();
    private final Map<String, TagObject> allTags = new TreeMap<>();
    private final Map<String, StepObject> allSteps = new TreeMap<>();

    private final StatusCounter featureCounter = new StatusCounter();

    private final String buildTime;

    private final OverviewReport featuresReport = new OverviewReport("Features");
    private final OverviewReport tagsReport = new OverviewReport("Tags");
	
	private List<TagObject> topLevel = new ArrayList<TagObject>();

    public ReportResult(List<Feature> features) {
        this.buildTime = getCurrentTime();

        for (Feature feature : features) {
            processFeature(feature);
        }
    }

    public List<Feature> getAllFeatures() {
        return mapToSortedList(allFeatures);
    }

    public List<TagObject> getAllTags() {
        return mapToSortedList(allTags.values());
    }
    
    public List<TagObject> getTopLevel(int hierarchyBy) {
    	
		List<List<String>> normalizedTags = normalizeAllTags(hierarchyBy);
		buildHierarchy(normalizedTags);
    	
    	System.out.println("Top Level: ");
    	for (TagObject tag : topLevel) {
    		System.out.println("Tag: " + tag.getName());
    	}
    	
        return topLevel;
    }
    
    private List<List<String>> normalizeAllTags(int hierarchyBy) {
    	List<List<String>> normalizedTags = new ArrayList<>();
    	for (Feature feature : allFeatures) {
    		Tag[] featureTags = feature.getTags();
    		
    		if (featureTags != null) {
    			List<String> tagArr = new ArrayList<>();
        		for (int index = 0; index < featureTags.length; index++) {
        			if (index < hierarchyBy) {
        				tagArr.add(featureTags[index].getName());
        			} else {
        				break;
        			}
        		}
        		
        		for (int index = featureTags.length-1; index >= 0; index--) {
        			if (index >= hierarchyBy) {
        				tagArr.add(0, featureTags[index].getName());
        			} else {
        				break;
        			}
        		}
        		normalizedTags.add(tagArr);
    		}
    	}
    	
    	return normalizedTags;
    }
    
    private void buildHierarchy(List<List<String>> normalizedTags) {
    	
    	for (List<String> featureTags : normalizedTags) {
    		for (int index = 0; index < featureTags.size(); index++) {
    			String parent = TOP_LEVEL;
    			if (index > 0) {
    				parent = featureTags.get(index-1);
    			}
    			
    			TagObject parentObj = allTags.get(parent);
				String tagName = featureTags.get(index);
				TagObject childObj = allTags.get(tagName);
				
				if (childObj != null) {
					if (parent == TOP_LEVEL) {
						if (!topLevel.contains(childObj)) {
							topLevel.add(childObj);
						}
					} else if (parentObj != null) {
						parentObj.addChild(childObj);
					} else {
						System.out.println("buildHierarchy cannot find parent - " + parent);
					}
				} else {
					System.out.println("buildHierarchy cannot find child - " + tagName);
				}
    		}
    	}
    }

    public List<StepObject> getAllSteps() {
        return mapToSortedList(allSteps.values());
    }

    public Reportable getFeatureReport() {
        return featuresReport;
    }

    public Reportable getTagReport() {
        return tagsReport;
    }

    public int getAllPassedFeatures() {
        return featureCounter.getValueFor(Status.PASSED);
    }

    public int getAllFailedFeatures() {
        return featureCounter.getValueFor(Status.FAILED);
    }

    public String getBuildTime() {
        return buildTime;
    }

    private void processFeature(Feature feature) {
        allFeatures.add(feature);

        for (Element element : feature.getElements()) {
            if (element.isScenario()) {
                featuresReport.incScenarioFor(element.getElementStatus());

                // all feature tags should be linked with scenario
                for (Tag tag : feature.getTags()) {
                    processTag(tag, element, feature.getStatus());
                }
            }

            // all element tags should be linked with element
            for (Tag tag : element.getTags()) {
                processTag(tag, element, element.getElementStatus());
            }

            Step[] steps = element.getSteps();
            for (Step step : steps) {
                featuresReport.incStepsFor(step.getStatus());
                featuresReport.incDurationBy(step.getDuration());
            }
            countSteps(steps);

            countSteps(element.getBefore());
            countSteps(element.getAfter());
        }
        featureCounter.incrementFor(feature.getStatus());
    }

    private void processTag(Tag tag, Element element, Status status) {

        TagObject tagObject = addTagObject(tag.getName());

        // if this element was not added by feature tag, add it as element tag
        if (tagObject.addElement(element)) {
            tagsReport.incScenarioFor(status);

            Step[] steps = element.getSteps();
            for (Step step : steps) {
                tagsReport.incStepsFor(step.getStatus());
                tagsReport.incDurationBy(step.getDuration());
            }
        }
    }

    private void countSteps(Resultsable[] steps) {
        for (Resultsable step : steps) {

            Match match = step.getMatch();
            // no match = could not find method that was matched to this step -> status is missing
            if (match == null) {
                continue;
            }

            String methodName = match.getLocation();
            // location is missing so there is no way to identify step
            if (StringUtils.isEmpty(methodName)) {
                continue;
            }
            addNewStep(step.getResult(), methodName);
        }
    }

    private void addNewStep(Result result, String methodName) {
        StepObject stepObject = allSteps.get(methodName);
        // if first occurrence of this location add element to the map
        if (stepObject == null) {
            stepObject = new StepObject(methodName);
        }

        // happens that report is not valid - does not contain information about result
        if (result != null) {
            stepObject.addDuration(result.getDuration(), Status.toStatus(result.getStatus()));
        } else {
            // when result is not available it means that something really went wrong (report is incomplete)
            // and for this case FAILED status is used to avoid problems during parsing
            stepObject.addDuration(0, Status.FAILED);
        }
        allSteps.put(methodName, stepObject);
    }

    private TagObject addTagObject(String name) {
    	TagObject tagObject = allTags.get(name);
        if (tagObject == null) {
            tagObject = new TagObject(name);
            allTags.put(tagObject.getName(), tagObject);
        }
        return tagObject;
    }

    public static String getCurrentTime() {
        return new SimpleDateFormat("dd MMM yyyy, HH:mm").format(new Date());
    }

    private static <T extends Comparable<? super T>> List<T> mapToSortedList(Collection<T> values) {
        List<T> list = new ArrayList<T>(values);
        Collections.sort(list);
        return list;
    }
}
