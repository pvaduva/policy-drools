/*-
 * ============LICENSE_START=======================================================
 * Base Package
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

use xacml;

DROP TABLE IF EXISTS XACML.SYSTEMLOGDB;
DROP TABLE IF EXISTS XACML.SEQUENCE;
DROP TABLE IF EXISTS XACML.RULEALGORITHMS;
DROP TABLE IF EXISTS XACML.ROLES;
DROP TABLE IF EXISTS XACML.POLICYALGORITHMS;
DROP TABLE IF EXISTS XACML.POLICY_MANANGEMENT;
DROP TABLE IF EXISTS XACML.PIPRESOLVERPARAMS;
DROP TABLE IF EXISTS XACML.PIPRESOLVER;
DROP TABLE IF EXISTS XACML.PIPCONFIGPARAMS;
DROP TABLE IF EXISTS XACML.PIPCONFIGURATION;
DROP TABLE IF EXISTS XACML.PIPTYPE;
DROP TABLE IF EXISTS XACML.OBADVICEEXPRESSIONS;
DROP TABLE IF EXISTS XACML.GLOBALROLESETTINGS;
DROP TABLE IF EXISTS XACML.FUNCTIONARGUMENTS;
DROP TABLE IF EXISTS XACML.FUNCTIONDEFINITION;
DROP TABLE IF EXISTS XACML.ECOMPNAME;
DROP TABLE IF EXISTS XACML.DECISIONSETTINGS;
DROP TABLE IF EXISTS XACML.ATTRIBUTEASSIGNMENT;
DROP TABLE IF EXISTS XACML.CONSTRAINTVALUES;
DROP TABLE IF EXISTS XACML.ATTRIBUTE;
DROP TABLE IF EXISTS XACML.OBADVICE;
DROP TABLE IF EXISTS XACML.CONSTRAINTTYPE;
DROP TABLE IF EXISTS XACML.CATEGORY;
DROP TABLE IF EXISTS XACML.DATATYPE;
DROP TABLE IF EXISTS XACML.ACTIONPOLICYDICT;
DROP TABLE IF EXISTS XACML.SERVICEGROUP; 
DROP TABLE IF EXISTS XACML.SECURITYZONE;
DROP TABLE IF EXISTS XACML.POLICYENTITY;
DROP TABLE IF EXISTS XACML.CONFIGURATIONDATAENTITY;
DROP TABLE IF EXISTS XACML.POLICYDBDAOENTITY;

DROP TABLE IF EXISTS XACML.POLICYSCORE;
DROP TABLE IF EXISTS XACML.ACTIONLIST;
DROP TABLE IF EXISTS XACML.PROTOCOLLIST;
DROP TABLE IF EXISTS XACML.TERM;
DROP TABLE IF EXISTS XACML.PREFIXLIST;
DROP TABLE IF EXISTS XACML.SCOPE; 
DROP TABLE IF EXISTS XACML.ENFORCINGTYPE;
DROP TABLE IF EXISTS XACML.PORTLIST;
DROP TABLE IF EXISTS XACML.GROUPSERVICELIST;
DROP TABLE IF EXISTS XACML.VSCLACTION;
DROP TABLE IF EXISTS XACML.VNFTYPE;
DROP TABLE IF EXISTS XACML.ADDRESSGROUP;

-- DROP SEQUENCE IF EXISTS XACML.SEQCONFIG;
-- DROP SEQUENCE IF EXISTS XACML.SEQPOLICY;

DROP TABLE IF EXISTS LOG.SEQUENCE; 
DROP TABLE IF EXISTS LOG.SYSTEMLOGDB; 

DROP VIEW IF EXISTS xacml.match_functions;
DROP VIEW IF EXISTS xacml.xacml.function_flattener;
DROP VIEW IF EXISTS xacml.xacml.higherorder_bag_functions;