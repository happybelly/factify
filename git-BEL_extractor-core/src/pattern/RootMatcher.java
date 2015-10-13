package pattern;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import knowledge_model.C_Facts;
import nlp.Sequence;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import utility.Debug;
import utility.Span;
import utility.utility;
import utility.Debug.DEBUG_CONFIG;

public class RootMatcher {
	private List<Object> matchers = new ArrayList<Object>();
	public boolean readMacher(String path) {
		JSONParser parser = new JSONParser();
		try {     
			Object obj = parser.parse(new FileReader(path));

			JSONObject jsonObject =  (JSONObject) obj;
			// loop array
			JSONArray rules = (JSONArray) jsonObject.get("Rules");
			Iterator<JSONObject> iterator = rules.iterator();
			while (iterator.hasNext()) {
				JSONObject rule = iterator.next();
				String type = (String) rule.get("type");
				String inputFileVersionFromRoot = (String) rule.get("inputFileVersion");
				String inputFilePath = (String) rule.get("inputFilePath");
				Debug.println(rule.toString(), DEBUG_CONFIG.debug_temp);
				Debug.println(inputFileVersionFromRoot, DEBUG_CONFIG.debug_temp);
				Debug.println(inputFilePath, DEBUG_CONFIG.debug_temp);
				Debug.println(type, DEBUG_CONFIG.debug_temp);
				if(type == null 
						|| inputFileVersionFromRoot == null 
						|| inputFilePath == null 
						|| (inputFilePath != null && !(new File(inputFilePath)).exists())
						|| (inputFilePath != null && (new File(inputFilePath)).exists() && !(new File(inputFilePath).isFile()))
						) {
					Debug.println("Parameter error for inputFilePath: " + inputFilePath, DEBUG_CONFIG.debug_warning);
					continue;
				}
				switch(type) {
				case "postag":
				{
					HashSet<String> posTags = readList(inputFilePath, inputFileVersionFromRoot);
					POSTagMatcher postTagMatcher = null;
					if(posTags != null)
						postTagMatcher = new POSTagMatcher(posTags, type, inputFileVersionFromRoot, inputFilePath);
					if(postTagMatcher != null) matchers.add(postTagMatcher);
					break;
				}
				case "regExp": 
				{
					HashSet<String> regExps = readList(inputFilePath, inputFileVersionFromRoot);
					RegularExpressionMatcher regExpMatcher = null;
					if(regExps != null)
						regExpMatcher = new RegularExpressionMatcher(regExps, type, inputFileVersionFromRoot, inputFilePath);
					if(regExpMatcher != null) matchers.add(regExpMatcher);
					break;
				}
				case "preBuiltWordList":
				{
					HashSet<String> wordList = readList(inputFilePath, inputFileVersionFromRoot);
					PreBuiltWordListMatcher preBuiltWordMatcher = null;
					if(wordList != null)
						preBuiltWordMatcher = new PreBuiltWordListMatcher(wordList, type, inputFileVersionFromRoot, inputFilePath);
					if(preBuiltWordMatcher != null) matchers.add(preBuiltWordMatcher);
					break;
				}
				default:
					
				}
				
			}
		} catch (FileNotFoundException e) {
			Debug.print("Error: File " + path + " does not exist!", DEBUG_CONFIG.debug_error);
			return false;
		} catch (IOException e) {
			Debug.print("Error: " + e.getMessage() + " when accessing file " + path, DEBUG_CONFIG.debug_error);
			return false;
		} catch (ParseException e) {
			Debug.print("Error: " + e.getMessage() + " when parsing jason file " + path, DEBUG_CONFIG.debug_error);
			return false;
		}
		if(this.matchers.size() == 0) {
			Debug.println("Fatal Error: no matcher is found!", DEBUG_CONFIG.debug_error);
			return false;
		}
		return true;
	}
	
	private HashSet<String> readList(String inputPath, String inputFileVersionFromRoot) {
		File inputFile = new File(inputPath);
		if(!inputFile.exists() || !inputFile.isFile()) {
			Debug.print("Input rule file does not exist or is not a file! Path: " + inputFile.getAbsolutePath(), DEBUG_CONFIG.debug_warning);
			return null;
		}
		HashSet<String> postags = new HashSet<String>();
		try {
			BufferedReader br = new BufferedReader(new FileReader(inputFile));
			String line = null;
			while((line = br.readLine()) != null) {
				line = line.trim();
				if(line.startsWith("--")) {
					if(line.startsWith("--version:")) {
						String version = line.substring("//version:".length());
						if(!version.equals(inputFileVersionFromRoot)) {
							Debug.println("Warning: inputFile has a version: " + version + " while the root file says " + inputFileVersionFromRoot, DEBUG_CONFIG.debug_warning);
						}
					}
				}else
					if(!line.trim().isEmpty()) postags.add(line);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
		return postags.size() > 0 ? postags : null;
	}
	
	public  C_Facts parsePara(List<Sequence> sentences, HashSet<Sequence> freSeq_, Map<String, Sequence> acronyms, Span pageRange) {
//		String para = "This bag costs 100. It is a million dollars. It's improved by 10 % with p <= 0.001.Mine is better than his.";
		if(this.matchers.size() == 0) {
			Debug.print("No matcher is found!", DEBUG_CONFIG.debug_error);
			return null;
		}
		utility util = new utility();
		List<List<Span>> allFacts = new ArrayList<List<Span>>();
		List<String> details = new ArrayList<String>();
		boolean printDetail = true;
		String detail = "";
		PatternMatcher pm = new PatternMatcher();
		for(Sequence s : sentences) {
//			Debug.println(s.sourceString);
			Debug.println(s.POSTags, DEBUG_CONFIG.debug_pattern);
			List<List<Span>> all = new ArrayList<List<Span>>();
			for(Object matcher : matchers)
			{
				if(matcher instanceof POSTagMatcher) {
					POSTagMatcher posTagMatcher = (POSTagMatcher) matcher;
					List<Span> result = posTagMatcher.Match(s);
					if(printDetail)	
						for(Span span : result) 
							Debug.println("POSTagMatcher (" + posTagMatcher.getInputFileName() + "): " + span.getCoveredText(s.sourceString), DEBUG_CONFIG.debug_pattern);
					all.add(result);
					for(Span span : result) {
						detail += "Rule " + posTagMatcher.getInputFilePath() + ": " + span.getCoveredText(s.sourceString) + "\r\n"; 
					}
				}
				if(matcher instanceof RegularExpressionMatcher) {
					RegularExpressionMatcher regExpMatcher = (RegularExpressionMatcher) matcher;
					List<Span> result = regExpMatcher.Match(s);
					if(printDetail)	
						for(Span span : result) 
							Debug.println("RegularExpressionMatcher (" + regExpMatcher.getInputFileName() + "): " + span.getCoveredText(s.sourceString), DEBUG_CONFIG.debug_pattern);
					all.add(result);
					for(Span span : result) {
						detail += "Rule " + regExpMatcher.getInputFilePath() + ": " + span.getCoveredText(s.sourceString) + "\r\n"; 
					}
				}
				if(matcher instanceof PreBuiltWordListMatcher) {
					PreBuiltWordListMatcher preBuiltWordMatcher = (PreBuiltWordListMatcher) matcher;
					List<Span> result = preBuiltWordMatcher.Match(s);
					if(printDetail)	
						for(Span span : result) 
							Debug.println("PreBuiltWordListMatcher (" + preBuiltWordMatcher.getInputFileName() + "): " + span.getCoveredText(s.sourceString), DEBUG_CONFIG.debug_pattern);
					all.add(result);
					for(Span span : result) {
						detail += "Rule " + preBuiltWordMatcher.getInputFilePath() + ": " + span.getCoveredText(s.sourceString) + "\r\n"; 
					}
				}
				{
					if(freSeq_ != null){
						List<Span> ngrams_s = pm.extractNGrams(s, freSeq_);
						if(printDetail)				for(Span span : ngrams_s) Debug.println("ngrams:" + span.getCoveredText(s.sourceString),DEBUG_CONFIG.debug_pattern);
						all.add(ngrams_s);
						for(Span span : ngrams_s) {
							detail += "Rule Freq NGrams: " + span.getCoveredText(s.sourceString) + "\r\n"; 
						}
					}
				}
				{
					
					List<Span> units = pm.extractUnits(s);
					if(printDetail) for(Span span : units) Debug.println("units:" + span.getCoveredText(s.sourceString),DEBUG_CONFIG.debug_pattern);
					all.add(units);
					for(Span span : units) {
						detail += "Rule Units: " + span.getCoveredText(s.sourceString) + "\r\n"; 
					}
					
				}
				{
					List<Span> textToNum = pm.extractTextToNum(s);
					if(printDetail)				for(Span span : textToNum) Debug.println("textToNum:" + span.getCoveredText(s.sourceString),DEBUG_CONFIG.debug_pattern);
					all.add(textToNum);
					for(Span span : textToNum) {
						detail += "Rule textToNum: " + span.getCoveredText(s.sourceString) + "\r\n"; 
					}
				}
				if(acronyms!= null){
//					acronyms.add("subunit");
					 List<Span> acros = pm.extractAcronyms(s, acronyms);
					if(printDetail)				for(Span acro : acros) Debug.println("acronyms:" + acro.getCoveredText(s.sourceString),DEBUG_CONFIG.debug_pattern);
					all.add(acros);
					for(Span span : acros) {
						detail += "Rule Acronyms: " + span.getCoveredText(s.sourceString) + "\r\n"; 
					}
				}
				{
					List<Span> parenthesis = pm.extractParenthesis(s);
					if(printDetail)				for(Span p : parenthesis) Debug.println("parenthesis:" + p.getCoveredText(s.sourceString),DEBUG_CONFIG.debug_pattern);
					all.add(parenthesis);
					
				}
				{
					List<Span> pvalues = pm.extractP_Value(s);
					if(printDetail)				for(Span span : pvalues) Debug.println("pvalues:" + span.getCoveredText(s.sourceString),DEBUG_CONFIG.debug_pattern);
					all.add(pvalues);
					for(Span span : pvalues) {
						detail += "Rule P Value: " + span.getCoveredText(s.sourceString) + "\r\n"; 
					}
				}
				
				
			}
			List<Span> after = pm.resolveSpans(all, s);
			allFacts.add(after);
			details.add(detail);
		}
		C_Facts cFact = pm.formFacts(allFacts, sentences, details, pageRange);
//		S_Facts sfact = new S_Facts(cFact);
//		sfact.mergeFacts();
//		sfact.printFacts();
		return cFact;
		
//		for(int i = 0; i < sentences.size(); i++) {
//			Debug.println(sentences.get(i).sourceString);
//			Debug.print("**\t");
//			for(Span span : allFacts.get(i)) Debug.print(span.getCoveredText(sentences.get(i).sourceString) + "\t");
//			Debug.println("**");
//		}
		
		
		
	}
}