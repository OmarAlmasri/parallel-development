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
    createTable($("#apdexTable"), {"supportsControllersDiscrimination": true, "overall": {"data": [0.98408, 500, 1500, "Total"], "isController": false}, "titles": ["Apdex", "T (Toleration threshold)", "F (Frustration threshold)", "Label"], "items": [{"data": [0.985, 500, 1500, "BENCH GET /api/products"], "isController": false}, {"data": [0.985, 500, 1500, "WARMUP GET /api/products/{threadId}"], "isController": false}, {"data": [0.845, 500, 1500, "SETUP POST /api/auth/login"], "isController": false}, {"data": [0.995, 500, 1500, "WARMUP GET /api/products"], "isController": false}, {"data": [0.9845, 500, 1500, "BENCH GET /api/products/{threadId}"], "isController": false}, {"data": [0.98275, 500, 1500, "BENCH GET /api/cart"], "isController": false}, {"data": [0.98575, 500, 1500, "BENCH GET /api/users/me"], "isController": false}, {"data": [0.985, 500, 1500, "BENCH GET /api/products?categoryId"], "isController": false}, {"data": [0.987, 500, 1500, "BENCH GET /api/categories"], "isController": false}, {"data": [0.99, 500, 1500, "WARMUP GET /api/products?categoryId"], "isController": false}, {"data": [0.995, 500, 1500, "WARMUP GET /api/categories"], "isController": false}]}, function(index, item){
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
    createTable($("#statisticsTable"), {"supportsControllersDiscrimination": true, "overall": {"data": ["Total", 12500, 0, 0.0, 187.19647999999864, 12, 1248, 154.0, 364.0, 445.0, 628.9899999999998, 274.4839701361441, 1364.7985459211682, 101.13053085199824], "isController": false}, "titles": ["Label", "#Samples", "FAIL", "Error %", "Average", "Min", "Max", "Median", "90th pct", "95th pct", "99th pct", "Transactions/s", "Received", "Sent"], "items": [{"data": ["BENCH GET /api/products", 2000, 0, 0.0, 197.1730000000002, 17, 953, 166.0, 377.0, 456.0, 612.0, 45.90103736344441, 1039.3572258990864, 16.846666867483705], "isController": false}, {"data": ["WARMUP GET /api/products/{threadId}", 100, 0, 0.0, 153.86999999999998, 28, 777, 126.5, 272.70000000000005, 425.2499999999994, 775.8899999999994, 5.089835598310175, 3.104799714969207, 1.882593000839823], "isController": false}, {"data": ["SETUP POST /api/auth/login", 100, 0, 0.0, 394.3299999999999, 88, 873, 347.0, 674.4000000000001, 776.9999999999995, 872.6399999999999, 5.0385448682420515, 3.2016409910817756, 1.2838448506071445], "isController": false}, {"data": ["WARMUP GET /api/products", 100, 0, 0.0, 169.86999999999998, 43, 651, 137.5, 339.0, 427.24999999999915, 649.2199999999991, 5.2010193998023615, 117.7695672101732, 1.9088858603786343], "isController": false}, {"data": ["BENCH GET /api/products/{threadId}", 2000, 0, 0.0, 185.07399999999984, 15, 1248, 154.0, 352.9000000000001, 433.9499999999998, 622.9000000000001, 46.00556667356751, 28.063395670876176, 17.01621911876337], "isController": false}, {"data": ["BENCH GET /api/cart", 2000, 0, 0.0, 198.82150000000019, 14, 1094, 165.0, 390.0, 474.9499999999998, 631.98, 46.09463228007098, 19.667643296688098, 16.737663203807415], "isController": false}, {"data": ["BENCH GET /api/users/me", 2000, 0, 0.0, 178.6590000000001, 12, 1086, 145.0, 355.0, 421.0, 616.98, 46.31344942571323, 24.09656209475732, 16.998030954751762], "isController": false}, {"data": ["BENCH GET /api/products?categoryId", 2000, 0, 0.0, 187.11849999999993, 14, 919, 155.5, 360.0, 439.0, 610.97, 45.95271465661834, 224.50948169645932, 17.44901761712198], "isController": false}, {"data": ["BENCH GET /api/categories", 2000, 0, 0.0, 171.6014999999997, 13, 1008, 137.5, 331.9000000000001, 408.89999999999964, 594.97, 46.06066189171138, 24.199839939199926, 16.995214729048158], "isController": false}, {"data": ["WARMUP GET /api/products?categoryId", 100, 0, 0.0, 159.59000000000003, 24, 671, 130.0, 295.40000000000003, 410.5999999999999, 670.81, 5.1179691898254775, 25.004478223041094, 1.9433788672654688], "isController": false}, {"data": ["WARMUP GET /api/categories", 100, 0, 0.0, 152.94999999999985, 32, 512, 117.5, 356.5000000000002, 378.9, 511.1799999999996, 5.010522096402445, 2.632481335805191, 1.8487554332848983], "isController": false}]}, function(index, item){
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
