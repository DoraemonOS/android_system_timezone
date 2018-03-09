/*
 * Copyright (C) 2017 The Android Open Source Project
 *
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
 */

package com.android.libcore.timezone.tzlookup;

import com.android.libcore.timezone.tzlookup.proto.CountryZonesFile;
import com.google.protobuf.TextFormat;
import com.ibm.icu.util.TimeZone;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.android.libcore.timezone.tzlookup.TestUtils.assertAbsent;
import static com.android.libcore.timezone.tzlookup.TestUtils.assertContains;
import static com.android.libcore.timezone.tzlookup.TestUtils.createFile;
import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TzLookupGeneratorTest {

    public static final String INVALID_TIME_ZONE_ID = "NOT_A_VALID_ID";

    private Path tempDir;

    @Before
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("TzLookupGeneratorTest");
    }

    @After
    public void tearDown() throws Exception {
        TestUtils.deleteDir(tempDir);
    }

    @Test
    public void invalidCountryZonesFile() throws Exception {
        String countryZonesFile = createFile(tempDir, "THIS IS NOT A VALID FILE");
        List<ZoneTabFile.CountryEntry> gbZoneTabEntries = createValidZoneTabEntriesGb();
        String zoneTabFile = createZoneTabFile(gbZoneTabEntries);
        String outputFile = Files.createTempFile(tempDir, "out", null /* suffix */).toString();

        TzLookupGenerator tzLookupGenerator =
                new TzLookupGenerator(countryZonesFile, zoneTabFile, outputFile);
        assertFalse(tzLookupGenerator.execute());
    }

    @Test
    public void invalidRulesVersion() throws Exception {
        CountryZonesFile.Country validGb = createValidCountryGb();

        // The IANA version won't match ICU's IANA version so we should see a failure.
        CountryZonesFile.CountryZones badIanaVersionCountryZones =
                createValidCountryZones(validGb).toBuilder().setIanaVersion("2001a").build();
        String countryZonesFile = createCountryZonesFile(badIanaVersionCountryZones);

        List<ZoneTabFile.CountryEntry> gbZoneTabEntries = createValidZoneTabEntriesGb();
        String zoneTabFile = createZoneTabFile(gbZoneTabEntries);

        String outputFile = Files.createTempFile(tempDir, "out", null /* suffix */).toString();

        TzLookupGenerator tzLookupGenerator =
                new TzLookupGenerator(countryZonesFile, zoneTabFile, outputFile);
        assertFalse(tzLookupGenerator.execute());

        Path outputFilePath = Paths.get(outputFile);
        assertEquals(0, Files.size(outputFilePath));
    }

    @Test
    public void countryWithNoTimeZoneMappings() throws Exception {
        // No zones found!
        CountryZonesFile.Country gbWithoutZones =
                createValidCountryGb().toBuilder().clearTimeZoneMappings().build();
        CountryZonesFile.CountryZones countryZones = createValidCountryZones(gbWithoutZones);
        String countryZonesFile = createCountryZonesFile(countryZones);

        String zoneTabFile = createZoneTabFile(createValidZoneTabEntriesGb());

        String outputFile = Files.createTempFile(tempDir, "out", null /* suffix */).toString();

        TzLookupGenerator tzLookupGenerator =
                new TzLookupGenerator(countryZonesFile, zoneTabFile, outputFile);
        assertFalse(tzLookupGenerator.execute());

        Path outputFilePath = Paths.get(outputFile);
        assertEquals(0, Files.size(outputFilePath));
    }

    @Test
    public void countryWithDuplicateTimeZoneMappings() throws Exception {
        // Duplicate zones found!
        CountryZonesFile.Country validCountryGb = createValidCountryGb();
        CountryZonesFile.Country gbWithDuplicateZones =
                validCountryGb.toBuilder()
                        .setDefaultTimeZoneId(validCountryGb.getTimeZoneMappings(0).getId())
                        .addAllTimeZoneMappings(validCountryGb.getTimeZoneMappingsList())
                        .build();
        CountryZonesFile.CountryZones countryZones =
                createValidCountryZones(gbWithDuplicateZones);
        String countryZonesFile = createCountryZonesFile(countryZones);

        String zoneTabFile = createZoneTabFile(createValidZoneTabEntriesGb());

        String outputFile = Files.createTempFile(tempDir, "out", null /* suffix */).toString();

        TzLookupGenerator tzLookupGenerator =
                new TzLookupGenerator(countryZonesFile, zoneTabFile, outputFile);
        assertFalse(tzLookupGenerator.execute());

        Path outputFilePath = Paths.get(outputFile);
        assertEquals(0, Files.size(outputFilePath));
    }

    @Test
    public void badDefaultId() throws Exception {
        // Set an invalid default.
        CountryZonesFile.Country validGb =
                createValidCountryGb().toBuilder()
                        .setDefaultTimeZoneId("NOT_A_TIMEZONE_ID")
                        .build();
        CountryZonesFile.CountryZones gbCountryZones = createValidCountryZones(validGb);
        String countryZonesFile = createCountryZonesFile(gbCountryZones);

        List<ZoneTabFile.CountryEntry> gbZoneTabEntries = createValidZoneTabEntriesGb();
        String zoneTabFile = createZoneTabFile(gbZoneTabEntries);

        String outputFile = Files.createTempFile(tempDir, "out", null /* suffix */).toString();

        TzLookupGenerator tzLookupGenerator =
                new TzLookupGenerator(countryZonesFile, zoneTabFile, outputFile);
        assertFalse(tzLookupGenerator.execute());

        Path outputFilePath = Paths.get(outputFile);
        assertEquals(0, Files.size(outputFilePath));
    }

    @Test
    public void explicitDefaultIdInvalid() throws Exception {
        // Set a valid default, but to one that isn't referenced by "gb".
        CountryZonesFile.Country validGb = createValidCountryGb().toBuilder()
                .setDefaultTimeZoneId(createValidCountryFr().getTimeZoneMappings(0).getId())
                .build();
        CountryZonesFile.CountryZones gbCountryZones = createValidCountryZones(validGb);
        String countryZonesFile = createCountryZonesFile(gbCountryZones);

        List<ZoneTabFile.CountryEntry> gbZoneTabEntries = createValidZoneTabEntriesGb();
        String zoneTabFile = createZoneTabFile(gbZoneTabEntries);

        String outputFile = Files.createTempFile(tempDir, "out", null /* suffix */).toString();

        TzLookupGenerator tzLookupGenerator =
                new TzLookupGenerator(countryZonesFile, zoneTabFile, outputFile);
        assertFalse(tzLookupGenerator.execute());

        Path outputFilePath = Paths.get(outputFile);
        assertEquals(0, Files.size(outputFilePath));
    }

    @Test
    public void calculatedDefaultZone() throws Exception {
        // Ensure there's no explicit default for "gb" and there's one zone.
        CountryZonesFile.Country validCountryGb = createValidCountryGb();
        assertEquals(1, validCountryGb.getTimeZoneMappingsCount());

        String gbTimeZoneId = validCountryGb.getTimeZoneMappings(0).getId();
        CountryZonesFile.Country gbWithoutDefault = validCountryGb.toBuilder()
                .clearDefaultTimeZoneId().build();
        List<ZoneTabFile.CountryEntry> gbZoneTabEntries = createValidZoneTabEntriesGb();

        String tzLookupXml = generateTzLookupXml(gbWithoutDefault, gbZoneTabEntries);

        // Check gb's time zone was defaulted.
        assertContains(tzLookupXml, "code=\"gb\" default=\"" + gbTimeZoneId + "\"");
    }

    @Test
    public void explicitDefaultZone() throws Exception {
        // Ensure there's an explicit default for "gb" and there's one zone.
        CountryZonesFile.Country validCountryGb = createValidCountryGb();
        String gbTimeZoneId = validCountryGb.getTimeZoneMappings(0).getId();
        CountryZonesFile.Country gbWithExplicitDefaultTimeZone =
                validCountryGb.toBuilder()
                        .setDefaultTimeZoneId(gbTimeZoneId)
                        .build();
        List<ZoneTabFile.CountryEntry> gbZoneTabEntries = createValidZoneTabEntriesGb();

        String tzLookupXml = generateTzLookupXml(gbWithExplicitDefaultTimeZone, gbZoneTabEntries);

        // Check gb's time zone was defaulted.
        assertContains(tzLookupXml, "code=\"gb\" default=\"" + gbTimeZoneId + "\"");
    }

    @Test
    public void countryZonesContainsNonLowercaseIsoCode() throws Exception {
        CountryZonesFile.Country validCountry = createValidCountryGb();
        CountryZonesFile.Country invalidCountry =
                createValidCountryGb().toBuilder().setIsoCode("Gb").build();

        CountryZonesFile.CountryZones countryZones =
                createValidCountryZones(validCountry, invalidCountry);
        String countryZonesFile = createCountryZonesFile(countryZones);

        List<ZoneTabFile.CountryEntry> gbZoneTabEntries = createValidZoneTabEntriesGb();
        String zoneTabFile = createZoneTabFile(gbZoneTabEntries);

        String outputFile = Files.createTempFile(tempDir, "out", null /* suffix */).toString();

        TzLookupGenerator tzLookupGenerator =
                new TzLookupGenerator(countryZonesFile, zoneTabFile, outputFile);
        assertFalse(tzLookupGenerator.execute());

        Path outputFilePath = Paths.get(outputFile);
        assertEquals(0, Files.size(outputFilePath));
    }

    @Test
    public void countryZonesContainsDuplicate() throws Exception {
        CountryZonesFile.Country validGb = createValidCountryGb();

        // The file contains "gb" twice.
        CountryZonesFile.CountryZones duplicateGbData =
                createValidCountryZones(validGb, validGb);
        String countryZonesFile = createCountryZonesFile(duplicateGbData);

        List<ZoneTabFile.CountryEntry> gbZoneTabEntries = createValidZoneTabEntriesGb();
        String zoneTabFile = createZoneTabFile(gbZoneTabEntries);

        String outputFile = Files.createTempFile(tempDir, "out", null /* suffix */).toString();

        TzLookupGenerator tzLookupGenerator =
                new TzLookupGenerator(countryZonesFile, zoneTabFile, outputFile);
        assertFalse(tzLookupGenerator.execute());

        Path outputFilePath = Paths.get(outputFile);
        assertEquals(0, Files.size(outputFilePath));
    }

    @Test
    public void countryZonesAndZoneTabCountryMismatch() throws Exception {
        // The two input files contain non-identical country ISO codes.
        CountryZonesFile.CountryZones countryZones =
                createValidCountryZones(createValidCountryGb(), createValidCountryFr());
        String countryZonesFile = createCountryZonesFile(countryZones);

        String zoneTabFile =
                createZoneTabFile(createValidZoneTabEntriesFr(), createValidZoneTabEntriesUs());

        String outputFile = Files.createTempFile(tempDir, "out", null /* suffix */).toString();

        TzLookupGenerator tzLookupGenerator =
                new TzLookupGenerator(countryZonesFile, zoneTabFile, outputFile);
        assertFalse(tzLookupGenerator.execute());

        Path outputFilePath = Paths.get(outputFile);
        assertEquals(0, Files.size(outputFilePath));
    }

    @Test
    public void countryZonesAndZoneTabDisagreeOnZones() throws Exception {
        CountryZonesFile.Country gbWithWrongZones =
                createValidCountryGb().toBuilder()
                        .clearTimeZoneMappings()
                        .addAllTimeZoneMappings(createValidCountryFr().getTimeZoneMappingsList())
                        .build();
        CountryZonesFile.CountryZones countryZones = createValidCountryZones(gbWithWrongZones);
        String countryZonesFile = createCountryZonesFile(countryZones);

        String zoneTabFile = createZoneTabFile(createValidZoneTabEntriesGb());

        String outputFile = Files.createTempFile(tempDir, "out", null /* suffix */).toString();

        TzLookupGenerator tzLookupGenerator =
                new TzLookupGenerator(countryZonesFile, zoneTabFile, outputFile);
        assertFalse(tzLookupGenerator.execute());

        Path outputFilePath = Paths.get(outputFile);
        assertEquals(0, Files.size(outputFilePath));
    }

    @Test
    public void duplicateEntriesInZoneTab() throws Exception {
        CountryZonesFile.Country validGbCountry = createValidCountryGb();
        CountryZonesFile.CountryZones countryZones = createValidCountryZones(validGbCountry);
        String countryZonesFile = createCountryZonesFile(countryZones);

        String zoneTabFileWithDupes = createZoneTabFile(
                createValidZoneTabEntriesGb(), createValidZoneTabEntriesGb());

        String outputFile = Files.createTempFile(tempDir, "out", null /* suffix */).toString();

        TzLookupGenerator tzLookupGenerator =
                new TzLookupGenerator(countryZonesFile, zoneTabFileWithDupes, outputFile);
        assertFalse(tzLookupGenerator.execute());

        Path outputFilePath = Paths.get(outputFile);
        assertEquals(0, Files.size(outputFilePath));
    }

    @Test
    public void incorrectOffset() throws Exception {
        CountryZonesFile.Country validGbCountry = createValidCountryGb();
        CountryZonesFile.Country.Builder gbWithWrongOffsetBuilder = validGbCountry.toBuilder();
        gbWithWrongOffsetBuilder.getTimeZoneMappingsBuilder(0).setUtcOffset("20:00").build();
        CountryZonesFile.Country gbWithWrongOffset = gbWithWrongOffsetBuilder.build();

        CountryZonesFile.CountryZones countryZones = createValidCountryZones(gbWithWrongOffset);
        String countryZonesFile = createCountryZonesFile(countryZones);

        String zoneTabFile = createZoneTabFile(createValidZoneTabEntriesGb());

        String outputFile = Files.createTempFile(tempDir, "out", null /* suffix */).toString();

        TzLookupGenerator tzLookupGenerator =
                new TzLookupGenerator(countryZonesFile, zoneTabFile, outputFile);
        assertFalse(tzLookupGenerator.execute());

        Path outputFilePath = Paths.get(outputFile);
        assertEquals(0, Files.size(outputFilePath));
    }

    @Test
    public void badTimeZoneMappingId() throws Exception {
        CountryZonesFile.Country validGbCountry = createValidCountryGb();
        CountryZonesFile.Country.Builder gbWithBadIdBuilder = validGbCountry.toBuilder();
        gbWithBadIdBuilder.setDefaultTimeZoneId(validGbCountry.getTimeZoneMappings(0).getId())
                .addTimeZoneMappingsBuilder().setId(INVALID_TIME_ZONE_ID).setUtcOffset("00:00");
        CountryZonesFile.Country gbWithBadId = gbWithBadIdBuilder.build();

        CountryZonesFile.CountryZones countryZones = createValidCountryZones(gbWithBadId);
        String countryZonesFile = createCountryZonesFile(countryZones);

        List<ZoneTabFile.CountryEntry> zoneTabEntriesWithBadId =
                new ArrayList<>(createValidZoneTabEntriesGb());
        zoneTabEntriesWithBadId.add(new ZoneTabFile.CountryEntry("GB", INVALID_TIME_ZONE_ID));
        String zoneTabFile = createZoneTabFile(zoneTabEntriesWithBadId);

        String outputFile = Files.createTempFile(tempDir, "out", null /* suffix */).toString();

        TzLookupGenerator tzLookupGenerator =
                new TzLookupGenerator(countryZonesFile, zoneTabFile, outputFile);
        assertFalse(tzLookupGenerator.execute());

        Path outputFilePath = Paths.get(outputFile);
        assertEquals(0, Files.size(outputFilePath));
    }

    @Test
    public void everUtc_true() throws Exception {
        CountryZonesFile.Country validCountryGb = createValidCountryGb();
        String tzLookupXml = generateTzLookupXml(validCountryGb, createValidZoneTabEntriesGb());

        // Check gb's entry contains everutc="y".
        assertContains(tzLookupXml, "everutc=\"y\"");
    }

    @Test
    public void everUtc_false() throws Exception {
        CountryZonesFile.Country validCountryFr = createValidCountryFr();
        String tzLookupXml = generateTzLookupXml(validCountryFr, createValidZoneTabEntriesFr());

        // Check fr's entry contains everutc="n".
        assertContains(tzLookupXml, "everutc=\"n\"");
    }

    @Test
    public void shownInPicker_false() throws Exception {
        CountryZonesFile.Country countryPrototype = createValidCountryFr();

        CountryZonesFile.TimeZoneMapping.Builder timeZoneMappingBuilder =
                countryPrototype.getTimeZoneMappings(0).toBuilder();
        timeZoneMappingBuilder.setShownInPicker(false);

        CountryZonesFile.Country.Builder countryBuilder = countryPrototype.toBuilder();
        countryBuilder.setTimeZoneMappings(0, timeZoneMappingBuilder);
        CountryZonesFile.Country country = countryBuilder.build();

        String tzLookupXml = generateTzLookupXml(country, createValidZoneTabEntriesFr());

        assertContains(tzLookupXml, "picker=\"n\"");
    }

    @Test
    public void shownInPicker_true() throws Exception {
        CountryZonesFile.Country countryPrototype = createValidCountryFr();

        CountryZonesFile.TimeZoneMapping.Builder timeZoneMappingBuilder =
                countryPrototype.getTimeZoneMappings(0).toBuilder();
        timeZoneMappingBuilder.setShownInPicker(true);

        CountryZonesFile.Country.Builder countryBuilder = countryPrototype.toBuilder();
        countryBuilder.setTimeZoneMappings(0, timeZoneMappingBuilder);
        CountryZonesFile.Country country = countryBuilder.build();

        String tzLookupXml = generateTzLookupXml(country, createValidZoneTabEntriesFr());

        // We should not see anything "picker="y" is the implicit default.
        assertAbsent(tzLookupXml, "picker=");
    }

    @Test
    public void notAfter() throws Exception {
        CountryZonesFile.Country country = createValidCountryUs();
        List<ZoneTabFile.CountryEntry> zoneTabEntries = createValidZoneTabEntriesUs();
        String tzLookupXml = generateTzLookupXml(country, zoneTabEntries);
        String[] expectedLines = {
                "<id>America/New_York</id>",
                "<id notafter=\"183535200000\">America/Detroit</id>",
                "<id notafter=\"162370800000\">America/Kentucky/Louisville</id>",
                "<id notafter=\"986108400000\">America/Kentucky/Monticello</id>",
                "<id notafter=\"1143961200000\">America/Indiana/Indianapolis</id>",
                "<id notafter=\"1205046000000\">America/Indiana/Vincennes</id>",
                "<id notafter=\"1194156000000\">America/Indiana/Winamac</id>",
                "<id notafter=\"1143961200000\">America/Indiana/Marengo</id>",
                "<id notafter=\"1143961200000\">America/Indiana/Petersburg</id>",
                "<id notafter=\"1143961200000\">America/Indiana/Vevay</id>",
                "<id>America/Chicago</id>",
                "<id notafter=\"1143961200000\">America/Indiana/Knox</id>",
                "<id notafter=\"120639600000\">America/Menominee</id>",
                "<id notafter=\"733910400000\">America/North_Dakota/Center</id>",
                "<id notafter=\"1081065600000\">America/North_Dakota/New_Salem</id>",
                "<id notafter=\"1162105200000\">America/Indiana/Tell_City</id>",
                "<id notafter=\"1300003200000\">America/North_Dakota/Beulah</id>",
                "<id>America/Denver</id>",
                "<id notafter=\"152092800000\">America/Boise</id>",
                "<id>America/Phoenix</id>",
                "<id>America/Los_Angeles</id>",
                "<id>America/Anchorage</id>",
                "<id notafter=\"452084400000\">America/Juneau</id>",
                "<id notafter=\"452084400000\">America/Yakutat</id>",
                "<id notafter=\"452084400000\">America/Nome</id>",
                "<id notafter=\"1446372000000\">America/Metlakatla</id>",
                "<id notafter=\"357127200000\">America/Sitka</id>",
                "<id>Pacific/Honolulu</id>",
                "<id>America/Adak</id>",
        };
        for (String expectedLine : expectedLines) {
            assertContains(tzLookupXml, expectedLine);
        }
    }

    private String generateTzLookupXml(CountryZonesFile.Country country,
            List<ZoneTabFile.CountryEntry> zoneTabEntries) throws Exception {

        CountryZonesFile.CountryZones countryZones = createValidCountryZones(country);
        String countryZonesFile = createCountryZonesFile(countryZones);

        String zoneTabFile = createZoneTabFile(zoneTabEntries);

        String outputFile = Files.createTempFile(tempDir, "out", null /* suffix */).toString();

        TzLookupGenerator tzLookupGenerator =
                new TzLookupGenerator(countryZonesFile, zoneTabFile, outputFile);
        assertTrue(tzLookupGenerator.execute());

        Path outputFilePath = Paths.get(outputFile);
        assertTrue(Files.exists(outputFilePath));

        return readFileToString(outputFilePath);
    }

    private static String readFileToString(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private String createZoneTabFile(List<ZoneTabFile.CountryEntry>... zoneTabEntriesLists)
            throws Exception {
        List<List<ZoneTabFile.CountryEntry>> entries = Arrays.asList(zoneTabEntriesLists);
        List<String> lines = entries.stream()
                .flatMap(List::stream)
                .map(country -> country.isoCode + "\tIgnored\t" + country.olsonId)
                .collect(Collectors.toList());
        return TestUtils.createFile(tempDir, lines.toArray(new String[0]));
    }

    private String createCountryZonesFile(CountryZonesFile.CountryZones countryZones) throws Exception {
        return TestUtils.createFile(tempDir, TextFormat.printToString(countryZones));
    }

    private static CountryZonesFile.CountryZones createValidCountryZones(
            CountryZonesFile.Country... countries) {
        CountryZonesFile.CountryZones.Builder builder =
                CountryZonesFile.CountryZones.newBuilder()
                        .setIanaVersion(TimeZone.getTZDataVersion());
        for (CountryZonesFile.Country country : countries) {
            builder.addCountries(country);
        }
        return builder.build();
    }

    private static CountryZonesFile.Country createValidCountryGb() {
        return CountryZonesFile.Country.newBuilder()
                .setIsoCode("gb")
                .addTimeZoneMappings(CountryZonesFile.TimeZoneMapping.newBuilder()
                        .setUtcOffset("00:00")
                        .setId("Europe/London"))
                .build();
    }

    private static CountryZonesFile.Country createValidCountryUs() {
        return CountryZonesFile.Country.newBuilder()
                .setIsoCode("us")
                .setDefaultTimeZoneId("America/New_York")
                .addTimeZoneMappings(CountryZonesFile.TimeZoneMapping.newBuilder()
                        .setUtcOffset("-5:00")
                        .setId("America/New_York")
                        .setPriority(10))
                .addTimeZoneMappings(CountryZonesFile.TimeZoneMapping.newBuilder()
                        .setUtcOffset("-5:00")
                        .setId("America/Detroit"))
                .addTimeZoneMappings(CountryZonesFile.TimeZoneMapping.newBuilder()
                        .setUtcOffset("-5:00")
                        .setId("America/Kentucky/Louisville"))
                .addTimeZoneMappings(CountryZonesFile.TimeZoneMapping.newBuilder()
                        .setUtcOffset("-5:00")
                        .setId("America/Kentucky/Monticello"))
                .addTimeZoneMappings(CountryZonesFile.TimeZoneMapping.newBuilder()
                        .setUtcOffset("-5:00")
                        .setId("America/Indiana/Indianapolis"))
                .addTimeZoneMappings(CountryZonesFile.TimeZoneMapping.newBuilder()
                        .setUtcOffset("-5:00")
                        .setId("America/Indiana/Vincennes")
                        .setPriority(9))
                .addTimeZoneMappings(CountryZonesFile.TimeZoneMapping.newBuilder()
                        .setUtcOffset("-5:00")
                        .setId("America/Indiana/Winamac"))
                .addTimeZoneMappings(CountryZonesFile.TimeZoneMapping.newBuilder()
                        .setUtcOffset("-5:00")
                        .setId("America/Indiana/Marengo"))
                .addTimeZoneMappings(CountryZonesFile.TimeZoneMapping.newBuilder()
                        .setUtcOffset("-5:00")
                        .setId("America/Indiana/Petersburg"))
                .addTimeZoneMappings(CountryZonesFile.TimeZoneMapping.newBuilder()
                        .setUtcOffset("-5:00")
                        .setId("America/Indiana/Vevay"))
                .addTimeZoneMappings(CountryZonesFile.TimeZoneMapping.newBuilder()
                        .setUtcOffset("-6:00")
                        .setId("America/Chicago")
                        .setPriority(10))
                .addTimeZoneMappings(CountryZonesFile.TimeZoneMapping.newBuilder()
                        .setUtcOffset("-6:00")
                        .setId("America/Indiana/Knox"))
                .addTimeZoneMappings(CountryZonesFile.TimeZoneMapping.newBuilder()
                        .setUtcOffset("-6:00")
                        .setId("America/Menominee"))
                .addTimeZoneMappings(CountryZonesFile.TimeZoneMapping.newBuilder()
                        .setUtcOffset("-6:00")
                        .setId("America/North_Dakota/Center"))
                .addTimeZoneMappings(CountryZonesFile.TimeZoneMapping.newBuilder()
                        .setUtcOffset("-6:00")
                        .setId("America/North_Dakota/New_Salem"))
                .addTimeZoneMappings(CountryZonesFile.TimeZoneMapping.newBuilder()
                        .setUtcOffset("-6:00")
                        .setId("America/Indiana/Tell_City")
                        .setPriority(9))
                .addTimeZoneMappings(CountryZonesFile.TimeZoneMapping.newBuilder()
                        .setUtcOffset("-6:00")
                        .setId("America/North_Dakota/Beulah"))
                .addTimeZoneMappings(CountryZonesFile.TimeZoneMapping.newBuilder()
                        .setUtcOffset("-7:00")
                        .setId("America/Denver")
                        .setPriority(10))
                .addTimeZoneMappings(CountryZonesFile.TimeZoneMapping.newBuilder()
                        .setUtcOffset("-7:00")
                        .setId("America/Boise"))
                .addTimeZoneMappings(CountryZonesFile.TimeZoneMapping.newBuilder()
                        .setUtcOffset("-7:00")
                        .setId("America/Phoenix"))
                .addTimeZoneMappings(CountryZonesFile.TimeZoneMapping.newBuilder()
                        .setUtcOffset("-8:00")
                        .setId("America/Los_Angeles"))
                .addTimeZoneMappings(CountryZonesFile.TimeZoneMapping.newBuilder()
                        .setUtcOffset("-9:00")
                        .setId("America/Anchorage")
                        .setPriority(10))
                .addTimeZoneMappings(CountryZonesFile.TimeZoneMapping.newBuilder()
                        .setUtcOffset("-9:00")
                        .setId("America/Juneau")
                        .setPriority(9))
                .addTimeZoneMappings(CountryZonesFile.TimeZoneMapping.newBuilder()
                        .setUtcOffset("-9:00")
                        .setId("America/Yakutat"))
                .addTimeZoneMappings(CountryZonesFile.TimeZoneMapping.newBuilder()
                        .setUtcOffset("-9:00")
                        .setId("America/Nome"))
                .addTimeZoneMappings(CountryZonesFile.TimeZoneMapping.newBuilder()
                        .setUtcOffset("-9:00")
                        .setId("America/Metlakatla"))
                .addTimeZoneMappings(CountryZonesFile.TimeZoneMapping.newBuilder()
                        .setUtcOffset("-9:00")
                        .setId("America/Sitka"))
                .addTimeZoneMappings(CountryZonesFile.TimeZoneMapping.newBuilder()
                        .setUtcOffset("-10:00")
                        .setId("Pacific/Honolulu"))
                .addTimeZoneMappings(CountryZonesFile.TimeZoneMapping.newBuilder()
                        .setUtcOffset("-10:00")
                        .setId("America/Adak"))
                .build();
    }

    private static CountryZonesFile.Country createValidCountryFr() {
        return CountryZonesFile.Country.newBuilder()
                .setIsoCode("fr")
                .addTimeZoneMappings(CountryZonesFile.TimeZoneMapping.newBuilder()
                        .setUtcOffset("01:00")
                        .setId("Europe/Paris"))
                .build();
    }

    private static List<ZoneTabFile.CountryEntry> createValidZoneTabEntriesGb() {
        return Arrays.asList(new ZoneTabFile.CountryEntry("GB", "Europe/London"));
    }

    private static List<ZoneTabFile.CountryEntry> createValidZoneTabEntriesUs() {
        return Arrays.asList(
                new ZoneTabFile.CountryEntry("US", "America/New_York"),
                new ZoneTabFile.CountryEntry("US", "America/Detroit"),
                new ZoneTabFile.CountryEntry("US", "America/Kentucky/Louisville"),
                new ZoneTabFile.CountryEntry("US", "America/Kentucky/Monticello"),
                new ZoneTabFile.CountryEntry("US", "America/Indiana/Indianapolis"),
                new ZoneTabFile.CountryEntry("US", "America/Indiana/Vincennes"),
                new ZoneTabFile.CountryEntry("US", "America/Indiana/Winamac"),
                new ZoneTabFile.CountryEntry("US", "America/Indiana/Marengo"),
                new ZoneTabFile.CountryEntry("US", "America/Indiana/Petersburg"),
                new ZoneTabFile.CountryEntry("US", "America/Indiana/Vevay"),
                new ZoneTabFile.CountryEntry("US", "America/Chicago"),
                new ZoneTabFile.CountryEntry("US", "America/Indiana/Tell_City"),
                new ZoneTabFile.CountryEntry("US", "America/Indiana/Knox"),
                new ZoneTabFile.CountryEntry("US", "America/Menominee"),
                new ZoneTabFile.CountryEntry("US", "America/North_Dakota/Center"),
                new ZoneTabFile.CountryEntry("US", "America/North_Dakota/New_Salem"),
                new ZoneTabFile.CountryEntry("US", "America/North_Dakota/Beulah"),
                new ZoneTabFile.CountryEntry("US", "America/Denver"),
                new ZoneTabFile.CountryEntry("US", "America/Boise"),
                new ZoneTabFile.CountryEntry("US", "America/Phoenix"),
                new ZoneTabFile.CountryEntry("US", "America/Los_Angeles"),
                new ZoneTabFile.CountryEntry("US", "America/Anchorage"),
                new ZoneTabFile.CountryEntry("US", "America/Juneau"),
                new ZoneTabFile.CountryEntry("US", "America/Sitka"),
                new ZoneTabFile.CountryEntry("US", "America/Metlakatla"),
                new ZoneTabFile.CountryEntry("US", "America/Yakutat"),
                new ZoneTabFile.CountryEntry("US", "America/Nome"),
                new ZoneTabFile.CountryEntry("US", "America/Adak"),
                new ZoneTabFile.CountryEntry("US", "Pacific/Honolulu"));
    }

    private static List<ZoneTabFile.CountryEntry> createValidZoneTabEntriesFr() {
        return Arrays.asList(
                new ZoneTabFile.CountryEntry("FR", "Europe/Paris"));
    }
}
