/*
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
var showControllersOnly = false;
var seriesFilter = "";
var filtersOnlySampleSeries = true;

/*
 * Add header in statistics table to group metrics by category
 * format
 *
 */
function summaryTableHeader(header) {
    var newRow = header.insertRow(-1);
    newRow.className = "tablesorter-no-sort";
    var cell = document.createElement('th');
    cell.setAttribute("data-sorter", false);
    cell.colSpan = 1;
    cell.innerHTML = "Requests";
    newRow.appendChild(cell);

    cell = document.createElement('th');
    cell.setAttribute("data-sorter", false);
    cell.colSpan = 3;
    cell.innerHTML = "Executions";
    newRow.appendChild(cell);

    cell = document.createElement('th');
    cell.setAttribute("data-sorter", false);
    cell.colSpan = 7;
    cell.innerHTML = "Response Times (ms)";
    newRow.appendChild(cell);

    cell = document.createElement('th');
    cell.setAttribute("data-sorter", false);
    cell.colSpan = 1;
    cell.innerHTML = "Throughput";
    newRow.appendChild(cell);

    cell = document.createElement('th');
    cell.setAttribute("data-sorter", false);
    cell.colSpan = 2;
    cell.innerHTML = "Network (KB/sec)";
    newRow.appendChild(cell);
}

/*
 * Populates the table identified by id parameter with the specified data and
 * format
 *
 */
function createTable(table, info, formatter, defaultSorts, seriesIndex, headerCreator) {
    var tableRef = table[0];

    // Create header and populate it with data.titles array
    var header = tableRef.createTHead();

    // Call callback is available
    if(headerCreator) {
        headerCreator(header);
    }

    var newRow = header.insertRow(-1);
    for (var index = 0; index < info.titles.length; index++) {
        var cell = document.createElement('th');
        cell.innerHTML = info.titles[index];
        newRow.appendChild(cell);
    }

    var tBody;

    // Create overall body if defined
    if(info.overall){
        tBody = document.createElement('tbody');
        tBody.className = "tablesorter-no-sort";
        tableRef.appendChild(tBody);
        var newRow = tBody.insertRow(-1);
        var data = info.overall.data;
        for(var index=0;index < data.length; index++){
            var cell = newRow.insertCell(-1);
            cell.innerHTML = formatter ? formatter(index, data[index]): data[index];
        }
    }

    // Create regular body
    tBody = document.createElement('tbody');
    tableRef.appendChild(tBody);

    var regexp;
    if(seriesFilter) {
        regexp = new RegExp(seriesFilter, 'i');
    }
    // Populate body with data.items array
    for(var index=0; index < info.items.length; index++){
        var item = info.items[index];
        if((!regexp || filtersOnlySampleSeries && !info.supportsControllersDiscrimination || regexp.test(item.data[seriesIndex]))
                &&
                (!showControllersOnly || !info.supportsControllersDiscrimination || item.isController)){
            if(item.data.length > 0) {
                var newRow = tBody.insertRow(-1);
                for(var col=0; col < item.data.length; col++){
                    var cell = newRow.insertCell(-1);
                    cell.innerHTML = formatter ? formatter(col, item.data[col]) : item.data[col];
                }
            }
        }
    }

    // Add support of columns sort
    table.tablesorter({sortList : defaultSorts});
}

$(document).ready(function() {

    // Customize table sorter default options
    $.extend( $.tablesorter.defaults, {
        theme: 'blue',
        cssInfoBlock: "tablesorter-no-sort",
        widthFixed: true,
        widgets: ['zebra']
    });

    var data = {"OkPercent": 100.0, "KoPercent": 0.0};
    var dataset = [
        {
            "label" : "FAIL",
            "data" : data.KoPercent,
            "color" : "#FF6347"
        },
        {
            "label" : "PASS",
            "data" : data.OkPercent,
            "color" : "#9ACD32"
        }];
    $.plot($("#flot-requests-summary"), dataset, {
        series : {
            pie : {
                show : true,
                radius : 1,
                label : {
                    show : true,
                    radius : 3 / 4,
                    formatter : function(label, series) {
                        return '<div style="font-size:8pt;text-align:center;padding:2px;color:white;">'
                            + label
                            + '<br/>'
                            + Math.round10(series.percent, -2)
                            + '%</div>';
                    },
                    background : {
                        opacity : 0.5,
                        color : '#000'
                    }
                }
            }
        },
        legend : {
            show : true
        }
    });

    // Creates APDEX table
    createTable($("#apdexTable"), {"supportsControllersDiscrimination": true, "overall": {"data": [0.97988, 500, 1500, "Total"], "isController": false}, "titles": ["Apdex", "T (Toleration threshold)", "F (Frustration threshold)", "Label"], "items": [{"data": [0.98375, 500, 1500, "BENCH GET /api/products"], "isController": false}, {"data": [0.995, 500, 1500, "WARMUP GET /api/products/{threadId}"], "isController": false}, {"data": [0.895, 500, 1500, "SETUP POST /api/auth/login"], "isController": false}, {"data": [0.95, 500, 1500, "WARMUP GET /api/products"], "isController": false}, {"data": [0.98275, 500, 1500, "BENCH GET /api/products/{threadId}"], "isController": false}, {"data": [0.97575, 500, 1500, "BENCH GET /api/cart"], "isController": false}, {"data": [0.984, 500, 1500, "BENCH GET /api/users/me"], "isController": false}, {"data": [0.97675, 500, 1500, "BENCH GET /api/products?categoryId"], "isController": false}, {"data": [0.98125, 500, 1500, "BENCH GET /api/categories"], "isController": false}, {"data": [0.98, 500, 1500, "WARMUP GET /api/products?categoryId"], "isController": false}, {"data": [0.98, 500, 1500, "WARMUP GET /api/categories"], "isController": false}]}, function(index, item){
        switch(index){
            case 0:
                item = item.toFixed(3);
                break;
            case 1:
            case 2:
                item = formatDuration(item);
                break;
        }
        return item;
    }, [[0, 0]], 3);

    // Create statistics table
    createTable($("#statisticsTable"), {"supportsControllersDiscrimination": true, "overall": {"data": ["Total", 12500, 0, 0.0, 195.10167999999953, 12, 1568, 159.0, 381.0, 470.9499999999989, 680.0, 271.2320444386582, 1348.6327065499827, 99.93239541292367], "isController": false}, "titles": ["Label", "#Samples", "FAIL", "Error %", "Average", "Min", "Max", "Median", "90th pct", "95th pct", "99th pct", "Transactions/s", "Received", "Sent"], "items": [{"data": ["BENCH GET /api/products", 2000, 0, 0.0, 195.21799999999976, 15, 945, 164.5, 366.0, 455.9499999999998, 668.95, 46.346719810905384, 1049.4504338125391, 17.010241900910714], "isController": false}, {"data": ["WARMUP GET /api/products/{threadId}", 100, 0, 0.0, 192.38000000000005, 53, 541, 159.5, 389.70000000000016, 459.6999999999997, 540.4999999999998, 5.233957918978332, 3.192714330576782, 1.9358999627080498], "isController": false}, {"data": ["SETUP POST /api/auth/login", 100, 0, 0.0, 396.4399999999999, 127, 1568, 345.0, 686.1000000000001, 786.3499999999999, 1565.4099999999987, 4.894283476898982, 3.1099730202623337, 1.2470863718676586], "isController": false}, {"data": ["WARMUP GET /api/products", 100, 0, 0.0, 244.51000000000008, 51, 1302, 157.5, 541.8000000000004, 785.5999999999985, 1300.9499999999994, 5.079236082893132, 115.01196001371395, 1.8641887666344983], "isController": false}, {"data": ["BENCH GET /api/products/{threadId}", 2000, 0, 0.0, 186.64550000000006, 14, 978, 149.5, 374.0, 458.89999999999964, 655.9100000000001, 46.47056089967006, 28.34704214879874, 17.188207949951206], "isController": false}, {"data": ["BENCH GET /api/cart", 2000, 0, 0.0, 212.5089999999999, 17, 1010, 179.0, 396.0, 497.9499999999998, 698.0, 46.539768231954206, 19.85757376553265, 16.89929884930423], "isController": false}, {"data": ["BENCH GET /api/users/me", 2000, 0, 0.0, 186.50250000000017, 12, 1003, 154.0, 362.9000000000001, 444.89999999999964, 645.96, 46.85925821794241, 24.38054257163609, 17.198354507860643], "isController": false}, {"data": ["BENCH GET /api/products?categoryId", 2000, 0, 0.0, 192.91499999999988, 15, 1171, 155.0, 386.0, 492.0, 682.97, 46.41555849520759, 226.77282049943142, 17.624767196964424], "isController": false}, {"data": ["BENCH GET /api/categories", 2000, 0, 0.0, 186.56599999999995, 13, 1065, 149.0, 377.9000000000001, 466.0, 668.95, 46.509464676061576, 24.435636714571412, 17.16081156109018], "isController": false}, {"data": ["WARMUP GET /api/products?categoryId", 100, 0, 0.0, 175.19999999999996, 37, 688, 132.5, 325.6, 439.84999999999997, 687.0899999999996, 5.290725358446642, 25.849120086238823, 2.0089772862546957], "isController": false}, {"data": ["WARMUP GET /api/categories", 100, 0, 0.0, 172.05999999999997, 41, 610, 136.0, 371.5, 470.89999999999975, 609.5199999999998, 5.233410090014654, 2.749584598074105, 1.9309954436623402], "isController": false}]}, function(index, item){
        switch(index){
            // Errors pct
            case 3:
                item = item.toFixed(2) + '%';
                break;
            // Mean
            case 4:
            // Mean
            case 7:
            // Median
            case 8:
            // Percentile 1
            case 9:
            // Percentile 2
            case 10:
            // Percentile 3
            case 11:
            // Throughput
            case 12:
            // Kbytes/s
            case 13:
            // Sent Kbytes/s
                item = item.toFixed(2);
                break;
        }
        return item;
    }, [[0, 0]], 0, summaryTableHeader);

    // Create error table
    createTable($("#errorsTable"), {"supportsControllersDiscrimination": false, "titles": ["Type of error", "Number of errors", "% in errors", "% in all samples"], "items": []}, function(index, item){
        switch(index){
            case 2:
            case 3:
                item = item.toFixed(2) + '%';
                break;
        }
        return item;
    }, [[1, 1]]);

        // Create top5 errors by sampler
    createTable($("#top5ErrorsBySamplerTable"), {"supportsControllersDiscrimination": false, "overall": {"data": ["Total", 12500, 0, "", "", "", "", "", "", "", "", "", ""], "isController": false}, "titles": ["Sample", "#Samples", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors"], "items": [{"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}]}, function(index, item){
        return item;
    }, [[0, 0]], 0);

});
