/*
 * Copyright 2019
 * Ubiquitous Knowledge Processing (UKP) Lab and FG Language Technology
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.clarin.webanno.api.annotation.exception;

public class IllegalFeatureValueException
    extends AnnotationException
{
    private static final long serialVersionUID = -5120144780593360107L;

    public IllegalFeatureValueException()
    {
        super();
    }
    
    public IllegalFeatureValueException(String message)
    {
        super(message);
    }

    public IllegalFeatureValueException(String aMessage, Throwable aCause)
    {
        super(aMessage, aCause);
    }

    public IllegalFeatureValueException(Throwable aCause)
    {
        super(aCause);
    }
}
