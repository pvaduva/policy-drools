/*-
 * ============LICENSE_START=======================================================
 * policy-persistence
 * ================================================================================
 * Copyright (C) 2017 AT&T Intellectual Property. All rights reserved.
 * ================================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ============LICENSE_END=========================================================
 */

package org.openecomp.policy.drools.persistence;


public abstract class DroolsPdpObject implements DroolsPdp{
	
	@Override
	public boolean equals(Object other){
		if(other instanceof DroolsPdp){
		return this.getPdpId().equals(((DroolsPdp)other).getPdpId());
		}else{
			return false;
		}
	}
	private int nullSafeCompare(Comparable one, Comparable two){
		if(one != null && two != null){
			return one.compareTo(two);
		}
		if(one == null && two != null){
			return -1;
		}
		if(one != null && two == null){
			return 1;
		}
		return 0;
	}
	@Override
	public int comparePriority(DroolsPdp other){
		if(nullSafeCompare(this.getSiteName(),other.getSiteName()) == 0){
		if(this.getPriority() != other.getPriority()){
			return this.getPriority() - other.getPriority();
		}
		return this.getPdpId().compareTo(other.getPdpId());
		} else {
			return nullSafeCompare(this.getSiteName(),other.getSiteName());
		}
	}
	@Override
	public int comparePriority(DroolsPdp other, String previousSite){
		if(previousSite == null || previousSite.equals("")){
			return comparePriority(other);
		}
		if(nullSafeCompare(this.getSiteName(),other.getSiteName()) == 0){
			if(this.getPriority() != other.getPriority()){
				return this.getPriority() - other.getPriority();
			}
			return this.getPdpId().compareTo(other.getPdpId());
		} else {
			return nullSafeCompare(this.getSiteName(),other.getSiteName());
		}
	}
	@Override
	public DroolsSession getSession(String sessionName){
		for(DroolsSession session : getSessions()){
			if(session.getSessionName().equals(sessionName)){
				return session;
			}
		}
		return null;
	}
	@Override
	public void setSessionId(String sessionName, long sessionId){
		for(DroolsSession session : getSessions()){
			if(session.getSessionName().equals(sessionName)){
				session.setSessionId(sessionId);
				return;
			}
		}
		DroolsSessionEntity newSession = new DroolsSessionEntity();
		DroolsPdpEntity pdpEntityWithPdpId = new DroolsPdpEntity();
		pdpEntityWithPdpId.setPdpId(this.getPdpId());
		newSession.setPdpEntity(pdpEntityWithPdpId);
		newSession.setPdpId(getPdpId());
		newSession.setSessionName(sessionName);
		newSession.setSessionId(sessionId);
		getSessions().add(newSession);
	}
}