/*
 * Copyright 2000-2021 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.artifacts.s3;

import com.amazonaws.services.s3.AmazonS3;
import java.util.Map;
import jetbrains.buildServer.artifacts.s3.serialization.XmlSerializer;
import jetbrains.buildServer.artifacts.s3.serialization.impl.XmlSerializerImpl;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public abstract class S3ClientResourceFetcher<T extends S3Dto> {
  @NotNull
  private static final XmlSerializer OUR_XML_SERIALIZER = new XmlSerializerImpl();

  @NotNull
  public String fetchAsXml(final Map<String, String> parameters) throws Exception {
    final T dto = S3Util.withS3Client(parameters, s3Client -> fetchDto(s3Client, parameters));
    return OUR_XML_SERIALIZER.serialize(dto);
  }

  @NotNull
  public Element fetchAsElement(final Map<String, String> parameters) throws Exception {
    final T dto = S3Util.withS3Client(parameters, s3Client -> fetchDto(s3Client, parameters));
    return OUR_XML_SERIALIZER.serializeAsElement(dto);
  }

  protected abstract T fetchDto(final AmazonS3 s3Client, final Map<String, String> parameters) throws Exception;
}
